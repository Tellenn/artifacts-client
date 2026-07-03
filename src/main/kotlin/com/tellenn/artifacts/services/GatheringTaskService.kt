package com.tellenn.artifacts.services

import com.tellenn.artifacts.db.documents.GatheringTaskDocument
import com.tellenn.artifacts.db.documents.SliceReservation
import com.tellenn.artifacts.db.repositories.GatheringTaskRepository
import com.tellenn.artifacts.models.GatheringTaskStatus
import com.tellenn.artifacts.models.ReservationStatus
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

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
     * Publie une tâche par manque positif. Les matériaux assemblés par le crafter
     * (compétence de craft) sont ignorés — le crafter les produit lui-même.
     */
    fun postShortfalls(materials: Map<String, Int>) {
        materials.forEach { (code, qty) ->
            if (qty <= 0) return@forEach
            val skill = materialResponsibility.skillFor(code) ?: return@forEach
            repository.upsertTarget(code, skill, qty)
        }
    }

    fun reserveSlice(materialCode: String, owner: String, maxSlice: Int): SliceReservation? =
        repository.reserveSlice(materialCode, owner, maxSlice)

    fun reportProduced(materialCode: String, reservationId: String, amount: Int) =
        repository.reportProduced(materialCode, reservationId, amount)

    fun releaseSlice(materialCode: String, reservationId: String, amount: Int) =
        repository.releaseSlice(materialCode, reservationId, amount)

    /**
     * Consomme les tranches ouvertes d'un matériau jusqu'à épuisement du pool : réserve, délègue la
     * production à [produce] (collecte + dépôt en banque, fournie par l'appelant), puis reporte.
     * Utilisé par le crafter pendant ses batchs de leveling et réutilisable par les futurs workers.
     *
     * - Échec de [produce] : la tranche est libérée puis l'exception remonte telle quelle.
     * - Échec de l'infra du pool : best-effort — log `warn` et arrêt propre (le TTL des
     *   réservations sert de filet pour un report/release perdu).
     *
     * @return le total effectivement produit et reporté.
     */
    fun produceOpenSlices(materialCode: String, owner: String, maxSlice: Int, produce: (quantity: Int) -> Unit): Int {
        var totalProduced = 0
        while (true) {
            val reservation = try {
                repository.reserveSlice(materialCode, owner, maxSlice)
            } catch (e: Exception) {
                logger.warn("Pool de récolte injoignable pour {} : {} — arrêt de la consommation", materialCode, e.message)
                return totalProduced
            } ?: return totalProduced

            try {
                produce(reservation.amount)
            } catch (e: Exception) {
                runCatching { repository.releaseSlice(materialCode, reservation.id, reservation.amount) }
                    .onFailure { logger.warn("Libération de tranche impossible pour {} — le TTL la restituera", materialCode) }
                throw e
            }
            runCatching { repository.reportProduced(materialCode, reservation.id, reservation.amount) }
                .onFailure { logger.warn("Report de production impossible pour {} — le TTL restituera la tranche", materialCode) }
            totalProduced += reservation.amount
        }
    }

    /**
     * Tâches ouvertes que le personnage peut produire : compétence dans [skills],
     * niveau du matériau couvert par le niveau du personnage, triées par ancienneté.
     */
    fun openTasksFor(skills: List<String>, levelBySkill: Map<String, Int>): List<GatheringTaskDocument> =
        repository.findBySkillInAndRemainingGreaterThan(skills, 0)
            .filter { task -> itemService.getItem(task.materialCode).level <= (levelBySkill[task.skill] ?: 0) }
            .sortedBy { it.createdAt }

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
        reservations = reservations.map { ReservationStatus(it.owner, it.amount, it.reservedAt) },
        createdAt = createdAt,
    )

    fun expireStaleReservations() =
        repository.expireStaleReservations(Instant.now().minus(CLAIM_TTL))

    companion object {
        private val logger = LogManager.getLogger(GatheringTaskService::class.java)
        private val CLAIM_TTL: Duration = Duration.ofMinutes(10)
    }
}
