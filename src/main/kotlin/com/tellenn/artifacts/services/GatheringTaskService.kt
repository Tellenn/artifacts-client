package com.tellenn.artifacts.services

import com.tellenn.artifacts.db.documents.GatheringTaskDocument
import com.tellenn.artifacts.db.documents.SliceReservation
import com.tellenn.artifacts.db.repositories.GatheringTaskRepository
import com.tellenn.artifacts.models.GatheringTaskStatus
import com.tellenn.artifacts.models.ReservationStatus
import org.apache.logging.log4j.LogManager
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Pool de tâches de récolte partagé : le crafter y publie ses manques de matériaux,
 * et tout personnage capable réserve des tranches pour les produire en parallèle.
 */
@Service
class GatheringTaskService(
    private val repository: GatheringTaskRepository,
    private val materialResponsibility: MaterialResponsibility,
    private val itemService: ItemService
) {

    /**
     * Ids des réservations détenues par une production en cours de ce process. Le bot étant
     * mono-instance, ce registre fait autorité : toute réservation en base absente d'ici est
     * orpheline (crash, redémarrage, release perdu) et sera restituée par le sweep.
     */
    private val activeReservations: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Publie une tâche par manque positif. Les matériaux assemblés par le crafter
     * (compétence de craft) sont ignorés — le crafter les produit lui-même.
     * [bankQuantities] est la photo du stock banque disponible par matériau au moment du post,
     * jointe à la task à titre informatif (le manque publié en est déjà net).
     */
    fun postShortfalls(materials: Map<String, Int>, bankQuantities: Map<String, Int> = emptyMap()) {
        materials.forEach { (code, qty) ->
            if (qty <= 0) return@forEach
            val skill = materialResponsibility.skillFor(code) ?: return@forEach
            repository.upsertTarget(code, skill, qty, bankQuantities[code] ?: 0)
        }
    }

    /**
     * Réserve brute, sans inscription au registre des productions en cours : une réservation
     * prise ici sera restituée comme orpheline par le sweep passé [ORPHAN_GRACE]. Pour produire,
     * passer par [produceOpenSlices] qui gère le cycle de vie complet.
     */
    fun reserveSlice(materialCode: String, owner: String, maxSlice: Int): SliceReservation? =
        repository.reserveSlice(materialCode, owner, maxSlice)

    fun reportProduced(materialCode: String, reservationId: String, amount: Int) =
        repository.reportProduced(materialCode, reservationId, amount)

    /**
     * Crédite à la tâche une production réalisée hors pool (complément direct du crafter) :
     * sans cette absorption, la tâche survit en zombie (remaining figé) et les workers
     * sur-produisent un matériau dont le besoin est déjà couvert. Best-effort : un échec est
     * journalisé, la purge des tâches périmées sert de filet.
     */
    fun absorbExternalProduction(materialCode: String, amount: Int) {
        if (amount <= 0) return
        runCatching { repository.absorbExternalProduction(materialCode, amount) }
            .onFailure {
                logger.warn(
                    "Absorption de production hors pool impossible pour {} — la purge des tâches périmées servira de filet",
                    materialCode
                )
            }
    }

    fun releaseSlice(materialCode: String, reservationId: String, amount: Int) =
        repository.releaseSlice(materialCode, reservationId, amount)

    /**
     * Consomme les tranches ouvertes d'un matériau jusqu'à épuisement du pool : réserve, délègue la
     * production à [produce] (collecte + dépôt en banque, fournie par l'appelant), puis reporte.
     * Utilisé par le crafter pendant ses batchs de leveling et réutilisable par les futurs workers.
     *
     * - Échec de [produce] : la tranche est libérée puis l'exception remonte telle quelle.
     * - Échec de l'infra du pool : best-effort — log `warn` et arrêt propre (le sweep des
     *   réservations orphelines sert de filet pour un report/release perdu).
     *
     * @return le total effectivement produit et reporté.
     */
    fun produceOpenSlices(materialCode: String, owner: String, maxSlice: Int, produce: (quantity: Int) -> Unit): Int {
        val slice = sliceSizeFor(materialCode, maxSlice)
        var totalProduced = 0
        while (true) {
            val reservation = try {
                repository.reserveSlice(materialCode, owner, slice)
            } catch (e: Exception) {
                logger.warn("Pool de récolte injoignable pour {} : {} — arrêt de la consommation", materialCode, e.message)
                return totalProduced
            } ?: return totalProduced

            activeReservations.add(reservation.id)
            try {
                try {
                    produce(reservation.amount)
                } catch (e: Exception) {
                    runCatching { repository.releaseSlice(materialCode, reservation.id, reservation.amount) }
                        .onFailure { logger.warn("Libération de tranche impossible pour {} — le sweep des orphelines la restituera", materialCode) }
                    throw e
                }
                runCatching { repository.reportProduced(materialCode, reservation.id, reservation.amount) }
                    .onFailure { logger.warn("Report de production impossible pour {} — le sweep des orphelines restituera la tranche", materialCode) }
                totalProduced += reservation.amount
            } finally {
                activeReservations.remove(reservation.id)
            }
        }
    }

    /**
     * Taille de tranche effective : les tâches mob sont bornées à [MAX_MOB_SLICE] pour qu'un
     * combattant ne monopolise pas toute la tâche (drops lents, partage entre combattants,
     * moins de perte si la réservation est orpheline). Pool injoignable ou tâche inconnue →
     * pas de plafond, la réservation tranchera.
     */
    private fun sliceSizeFor(materialCode: String, maxSlice: Int): Int {
        val skill = runCatching { repository.findById(materialCode).orElse(null)?.skill }.getOrNull()
        return if (skill == MOB_SKILL) maxSlice.coerceAtMost(MAX_MOB_SLICE) else maxSlice
    }

    /**
     * Tâches ouvertes que le personnage peut produire : compétence dans [skills],
     * niveau du matériau couvert par le niveau du personnage. Triées par priorité de
     * compétence (ordre de [skills] : sa profession d'abord, `mob` en dernier via
     * [com.tellenn.artifacts.jobs.GenericJob.poolSkillsFor]) puis par ancienneté — un
     * récolteur draine ses tâches de métier avant de partir sur des drops de monstre.
     */
    fun openTasksFor(skills: List<String>, levelBySkill: Map<String, Int>): List<GatheringTaskDocument> =
        repository.findBySkillInAndRemainingGreaterThan(skills, 0)
            .filter { task -> itemService.getItem(task.materialCode).level <= (levelBySkill[task.skill] ?: 0) }
            .sortedWith(compareBy({ skills.indexOf(it.skill) }, { it.createdAt }))

    /** Vue synthétique de la file, triée par ancienneté (ordre de consommation des workers). */
    fun getQueueStatus(): List<GatheringTaskStatus> =
        repository.findAll()
            .sortedBy { it.createdAt }
            .map { it.toStatus() }

    private fun GatheringTaskDocument.toStatus() = GatheringTaskStatus(
        materialCode = materialCode,
        skill = skill,
        targetQuantity = targetQuantity,
        producedQuantity = producedQuantity,
        remaining = remaining,
        reserved = reservations.sumOf { it.amount },
        progressPercent = if (targetQuantity > 0) producedQuantity * 100 / targetQuantity else 0,
        bankQuantityAtPost = bankQuantityAtPost,
        reservations = reservations.map { ReservationStatus(it.owner, it.amount, it.reservedAt) },
        createdAt = createdAt,
    )

    /**
     * Filet de sécurité du pool : restitue les réservations orphelines — présentes en base mais
     * détenues par aucune production en cours de ce process (crash, redémarrage, release perdu).
     * Une tranche activement produite n'est jamais volée, quelle que soit sa durée. Premier
     * passage dès le démarrage (pas d'`initialDelay`) : le registre encore vide rend orphelin
     * tout reliquat de l'arrêt précédent. [ORPHAN_GRACE] couvre la fenêtre entre l'écriture de
     * la réservation en base et son inscription au registre.
     * NOTE : registre local au process — à revoir si plusieurs instances partagent la base.
     */
    @Scheduled(fixedRate = ORPHAN_SWEEP_MS)
    fun releaseOrphanedReservations() =
        repository.releaseOrphanedReservations(activeReservations.toSet(), Instant.now().minus(ORPHAN_GRACE))

    /**
     * Filet contre les tâches zombies : une tâche sans réservation ni post depuis [STALE_TASK_TTL]
     * appartient à un batch terminé (besoin couvert hors pool) ou abandonné — la garder ferait
     * sur-produire les workers. Premier passage dès le démarrage pour purger les reliquats.
     */
    @Scheduled(fixedRate = STALE_SWEEP_MS)
    fun purgeStaleTasks() {
        val deleted = repository.deleteStaleTasks(Instant.now().minus(STALE_TASK_TTL))
        if (deleted > 0) {
            logger.info("Purge de {} tâche(s) de récolte périmée(s) (aucune activité depuis {})", deleted, STALE_TASK_TTL)
        }
    }

    companion object {
        private val logger = LogManager.getLogger(GatheringTaskService::class.java)
        private const val ORPHAN_SWEEP_MS = 60_000L
        private val ORPHAN_GRACE: Duration = Duration.ofMinutes(2)
        private const val STALE_SWEEP_MS = 3_600_000L
        private val STALE_TASK_TTL: Duration = Duration.ofHours(24)
        private const val MOB_SKILL = "mob"
        private const val MAX_MOB_SLICE = 20
    }
}
