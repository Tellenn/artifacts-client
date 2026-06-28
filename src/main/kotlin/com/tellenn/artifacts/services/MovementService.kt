package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.exceptions.CharacterAlreadyMapException
import com.tellenn.artifacts.exceptions.UnreachableMapException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.MapData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.abs

/**
 * Service for handling character movement.
 * Provides methods to move a character to a specific cell.
 */
@Service
class MovementService(
    private val movementClient: MovementClient,
    private val accountClient: AccountClient,
    private val mapService: MapService,
    private val bankService: BankService,
    private val teleportService: TeleportService
) {
    private val log = LoggerFactory.getLogger(MovementService::class.java)

    /**
     * Moves a character to a specific cell.
     * If the character is already at the destination, no movement is performed.
     *
     * @param mapId The mapId of the destination
     * @param character The character object. It's used to check if the character is already at the destination
     * @return The updated character object
     */
    fun moveCharacterToCell(mapId: Int, character: ArtifactsCharacter): ArtifactsCharacter {
        if (character.mapId == mapId) {
            log.debug("Character ${character.name} is already at position $mapId, skipping movement call")
            return character
        }

        val teleported = tryTeleportTowards(character, mapId)
            ?: return walkToCell(mapId, character)

        // Une potion peut nous déposer dans la bonne région sans atteindre la
        // case exacte : on termine alors le trajet à pied.
        return if (teleported.mapId == mapId) teleported else walkToCell(mapId, teleported)
    }

    /**
     * Tente de se rapprocher de [mapId] via une potion de téléport.
     * Retourne `null` (téléportation non effectuée) si aucune potion adaptée
     * n'existe ou si la destination est assez proche pour s'y rendre à pied.
     */
    private fun tryTeleportTowards(character: ArtifactsCharacter, mapId: Int): ArtifactsCharacter? {
        val destinationMap = mapService.findByMapId(mapId) ?: return null
        if (isWithinWalkingRange(character, destinationMap)) return null

        val potion = teleportService.findPotionForDestination(character, mapId) ?: return null
        log.info("{} uses {} to get closer to via {}", character.name, potion.code, mapId)
        return teleportService.use(character, potion.code)
    }

    /**
     * Vrai si la destination est dans la même région et à portée de marche
     * (≤ [TELEPORT_MIN_CELLS] cases) : inutile de gâcher une potion.
     */
    private fun isWithinWalkingRange(character: ArtifactsCharacter, destination: MapData): Boolean {
        val originMap = mapService.findByMapId(character.mapId)
        if (originMap?.region != destination.region) return false
        val cells = abs(character.x - destination.x) + abs(character.y - destination.y)
        return cells <= TELEPORT_MIN_CELLS
    }

    /**
     * Déplacement classique (à pied) vers [mapId], sans recours aux potions —
     * intra-région via l'API de mouvement, inter-région via les transitions.
     */
    private fun walkToCell(mapId: Int, character: ArtifactsCharacter): ArtifactsCharacter {
        val destinationMap = mapService.findByMapId(mapId)
        val originMap = mapService.findByMapId(character.mapId)
        if (destinationMap?.region != originMap?.region) {
            return transitionsFromRegions(character, originMap!!, destinationMap!!)
        }
        return try {
            movementClient.move(character.name, destinationMap!!.mapId).data.character
        } catch (e: CharacterAlreadyMapException) {
            log.debug("Tried to move while the character was already here", e)
            accountClient.getCharacter(character.name).data
        }
    }

    fun moveCharacterToMaster(masterType: String, character: ArtifactsCharacter): ArtifactsCharacter {
        val map = mapService.findClosestMap(
            character = character,
            contentType = "tasks_master",
            contentCode = masterType
        )
        return moveCharacterToCell(map.mapId, character)
    }

    fun moveToNpc(character: ArtifactsCharacter, npcName: String): ArtifactsCharacter {
        // Les PNJ sont souvent liés à des événements : leur position stockée en base
        // peut être périmée, on interroge donc l'API en direct pour la localiser.
        val map = mapService.findClosestMapFromApi(
            character = character,
            contentType = "npc",
            contentCode = npcName
        )

        return moveCharacterToCell(map.mapId, character)
    }

    /**
     * This function will handle transitions between regions for characters
     * There may be multiple transition required if a region does not have a "direct" transition.
     */
    fun transitionsFromRegions(character: ArtifactsCharacter, originMap: MapData, destinationMap: MapData): ArtifactsCharacter {
        val path = mapService.findTransitionPath(originMap.region!!, destinationMap.region!!)
        if (path.isEmpty()) {
            log.warn("No transition path found from region ${originMap.region} to ${destinationMap.region}")
            throw UnreachableMapException(destinationMap, character.name)
        }
        var canUsePath = true
        path.forEach { transitionMapper ->
            transitionMapper.conditions?.forEach { condition ->
                canUsePath = when (condition.operator) {
                    "cost", "has_item" -> {
                        canUsePath && bankService.isInBank(condition.code, condition.value)
                    }
                    "achievement_unlocked" -> {
                        canUsePath && accountClient.getAccountAchievements(character.account, true).data.any { it.code == condition.code }
                    }
                    else -> {
                        false
                    }
                }
            }
        }
        if(!canUsePath){
            throw UnreachableMapException(destinationMap, character.name)
        }
        var newCharacter = character
        path.forEach { transitionMapper ->
            transitionMapper.conditions?.forEach { condition ->
                when (condition.operator) {
                    "cost", "has_item" -> {
                        if(condition.code == "gold"){
                            newCharacter = moveToBank(newCharacter)
                            newCharacter = bankService.withdrawMoney(newCharacter, condition.value)
                        }else {
                            newCharacter = bankService.withdrawOne(condition.code, condition.value, newCharacter)
                        }
                    }
                    else -> {
                        log.trace("no condition to fulfill")
                    }
                }
            }
        }
        var currentCharacter = character
        path.forEach { transitionMapper ->
            // Move to the map where the transition is located
            currentCharacter = moveCharacterToCell( transitionMapper.sourceMapData.mapId,currentCharacter)
            // Perform the transition
            currentCharacter = movementClient.transition(currentCharacter.name).data.character
        }

        // After all transitions, move to the final destination map if needed
        return moveCharacterToCell(destinationMap.mapId, currentCharacter)
    }

    /**
     * Moves a character to the closest bank if they're not already there.
     *
     * @param character The character to move to the bank
     * @param checkAchievement Whether to check if the bank is reachable based on achievements
     * @return The updated character after moving to the bank, or the original character if already at a bank
     */
    fun moveToBank(character: ArtifactsCharacter, checkAchievement: Boolean = true): ArtifactsCharacter {
        val closestBank = mapService.findClosestMap(character = character, contentCode = "bank", checkAchievement = checkAchievement)
        // Déjà à la banque : ne rien faire (surtout pas gâcher une potion).
        if (character.mapId == closestBank.mapId) {
            return character
        }
        // moveCharacterToCell applique la logique de téléport (distance + garde-fou
        // « rapprochement ») : la potion n'est utilisée que si elle aide réellement.
        return moveCharacterToCell(closestBank.mapId, character)
    }

    fun moveToGrandExchange(character: ArtifactsCharacter): ArtifactsCharacter {
        val geMap = mapService.findClosestMap(character = character, contentCode = "grandexchange")
        return moveCharacterToCell(geMap.mapId, character)
    }

    companion object {
        /** Au-delà de cette distance (en cases, même région), la potion devient rentable. */
        private const val TELEPORT_MIN_CELLS = 3
    }
}
