package com.tellenn.artifacts.services

import com.tellenn.artifacts.models.ArtifactsCharacter
import java.io.InterruptedIOException
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

/**
 * Résultat d'une passe de production sur le pool de récolte partagé.
 * [produced] est la somme des quantités effectivement produites sur toutes les tâches
 * traitées (0 si le pool était vide ou si toutes les tâches ont échoué) — un appelant
 * doit s'en servir pour décider s'il retente le pool ou retombe sur son comportement
 * normal, plutôt que de reboucler indéfiniment sur une tâche empoisonnée.
 */
data class PoolWorkResult(val character: ArtifactsCharacter, val produced: Int)

/**
 * Fait produire à un personnage les tranches ouvertes du pool de récolte partagé :
 * liste les tâches à sa portée, réserve des tranches bornées par son inventaire,
 * collecte, dépose en banque et valide la production. Le cycle réserver → produire →
 * valider → libérer-sur-échec est porté par [GatheringTaskService.produceOpenSlices].
 */
@Service
class GatheringWorkerService(
    private val gatheringTaskService: GatheringTaskService,
    private val gatheringService: GatheringService,
    private val movementService: MovementService,
    private val bankService: BankService,
    private val itemService: ItemService,
) {

    /**
     * Produit toutes les tranches ouvertes que [character] peut couvrir avec [skills].
     * Une tâche en échec est journalisée et n'empêche pas les suivantes (sa tranche a
     * déjà été libérée par `produceOpenSlices`) ; sa contribution au total produit est
     * alors nulle. Une [InterruptedException] (ou une [InterruptedIOException], dont le
     * flag est restauré) est en revanche propagée immédiatement — le framework de mission
     * ([ThreadService]) s'appuie sur ces interruptions pour suspendre les jobs. Retourne le
     * personnage à jour et le total produit, pour permettre à l'appelant de détecter une
     * tâche empoisonnée (rien produit) et de ne pas reboucler indéfiniment sur le pool.
     */
    fun workOpenTasks(
        character: ArtifactsCharacter,
        skills: List<String>,
        levelBySkill: Map<String, Int>,
        allowFight: Boolean = false,
    ): PoolWorkResult {
        var newCharacter = character
        var totalProduced = 0
        gatheringTaskService.openTasksFor(skills, levelBySkill).forEach { task ->
            try {
                val unitSize = itemService.getInvSizeToCraft(itemService.getItem(task.materialCode))
                val chunk = levelingGatherChunkSize(unitSize, task.remaining, newCharacter.inventoryMaxItems)
                totalProduced += gatheringTaskService.produceOpenSlices(task.materialCode, newCharacter.name, chunk) { quantity ->
                    newCharacter = gatheringService.craftOrGather(
                        newCharacter, task.materialCode, quantity,
                        allowFight = allowFight, shouldTrain = false
                    )
                    newCharacter = movementService.moveToBank(newCharacter)
                    newCharacter = bankService.emptyInventory(newCharacter)
                }
            } catch (e: InterruptedException) {
                throw e
            } catch (e: InterruptedIOException) {
                Thread.currentThread().interrupt()
                throw e
            } catch (e: Exception) {
                logger.warn(
                    "Production pool échouée pour {} par {} : {} — passage à la tâche suivante",
                    task.materialCode, newCharacter.name, e.message
                )
            }
        }
        return PoolWorkResult(newCharacter, totalProduced)
    }

    /** Vrai s'il reste au moins une tâche ouverte que ces compétences/niveaux peuvent produire. */
    fun hasOpenTasks(skills: List<String>, levelBySkill: Map<String, Int>): Boolean =
        gatheringTaskService.openTasksFor(skills, levelBySkill).isNotEmpty()

    companion object {
        private val logger = LogManager.getLogger(GatheringWorkerService::class.java)
    }
}
