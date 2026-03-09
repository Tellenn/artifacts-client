package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.exceptions.CharacterAlreadyMapException
import com.tellenn.artifacts.exceptions.UnreachableMapException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.MapData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for handling character movement.
 * Provides methods to move a character to a specific cell.
 */
@Service
class MovementService(
    private val movementClient: MovementClient,
    private val accountClient: AccountClient,
    private val mapService: MapService,
    private val bankService: BankService
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
        val destinationMap = mapService.findByMapId(mapId)
        val originMap = mapService.findByMapId(character.mapId)
        if(destinationMap?.region != originMap?.region){
            return transitionsFromRegions(character, originMap!!, destinationMap!!)
        }
        try {
            return movementClient.move(character.name, destinationMap!!.mapId).data.character
        }catch (e: CharacterAlreadyMapException){
                log.debug("Tried to move while the character was already here",e)
                return accountClient.getCharacter(character.name).data
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
        val map = mapService.findClosestMap(
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
            throw Exception("No transition path found from region ${originMap.region} to ${destinationMap.region}")
        }
        var canUsePath = true
        path.forEach { transitionMapper ->
            transitionMapper.conditions?.forEach { condition ->
                canUsePath = when (condition.operator) {
                    "cost", "has_item" -> {
                        canUsePath && bankService.isInBank(condition.code, condition.value)
                    }
                    "achievement_unlocked" -> {
                        canUsePath && accountClient.getAccountAchievement("Tellenn", true).data.any { it.code == condition.code }
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
     * @return The updated character after moving to the bank, or the original character if already at a bank
     */
    fun moveToBank(character: ArtifactsCharacter): ArtifactsCharacter {
        val closestBank = mapService.findClosestMap(character = character, contentCode = "bank")
        if (character.x == closestBank.x && character.y == closestBank.y) {
            return character
        }
        return moveCharacterToCell(closestBank.mapId, character)
    }
}
