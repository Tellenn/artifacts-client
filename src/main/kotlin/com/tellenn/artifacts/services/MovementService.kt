package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.db.repositories.ItemRepository
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
    private val mapService: MapService,
    private val itemRepository: ItemRepository
) {
    private val logger = LoggerFactory.getLogger(MovementService::class.java)

    /**
     * Moves a character to a specific cell.
     * If the character is already at the destination, no movement is performed.
     *
     * @param x The x-coordinate of the destination
     * @param y The y-coordinate of the destination
     * @param character The character object. It's used to check if the character is already at the destination
     * @return The updated character object
     */
    fun moveCharacterToCell(x: Int, y: Int, character: ArtifactsCharacter): ArtifactsCharacter {
        // If character is provided and already at the destination, skip the API call
        if (character.x == x && character.y == y) {
            logger.debug("Character ${character.name} is already at position ($x, $y), skipping movement call")
            return character
        }

        return movementClient.move(character.name, x, y).data.character
    }

    fun moveCharacterToCell(mapId: Int, character: ArtifactsCharacter): ArtifactsCharacter {
        val destinationMap = mapService.findByMapId(mapId)
        val originMap = mapService.findByMapId(character.mapId)
        if(destinationMap?.region != originMap?.region){
            return transitionsFromRegions(character, originMap!!, destinationMap!!)
        }else{
            return movementClient.move(character.name, destinationMap!!.mapId).data.character
        }

    }

    fun moveCharacterToMaster(masterType: String, character: ArtifactsCharacter): ArtifactsCharacter {
        val map = mapService.findClosestMap(
            character = character,
            contentType = "tasks_master",
            contentCode = masterType
        )

        return moveCharacterToCell(map.x, map.y, character)
    }

    fun moveToNpc(character: ArtifactsCharacter, npcName: String): ArtifactsCharacter {
        val map = mapService.findClosestMap(
            character = character,
            contentType = "npc",
            contentCode = npcName
        )

        return moveCharacterToCell(map.x, map.y, character)
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
        path.forEach { transitionMapper ->
            transitionMapper.conditions?.forEach { condition ->
                when (condition.operator) {
                    "cost", "has_item" -> {
                        itemRepository.findByCode(condition.code) ?: throw Exception("Item ${condition.code} does not exist in database")
                    }
                    "achievement_unlocked" -> {
                        // TODO : Check that the character has the achievement
                    }
                    else -> {
                        // TODO : handle other conditions
                        return character
                    }
                }
            }
        }
        // TODO : Satisfy the conditions for the path
        var currentCharacter = character
        path.forEach { transitionMapper ->
            // Move to the map where the transition is located
            currentCharacter = moveCharacterToCell(
                transitionMapper.sourceMapData.x,
                transitionMapper.sourceMapData.y,
                currentCharacter
            )
            // Perform the transition
            currentCharacter = movementClient.transition(currentCharacter.name).data.character
        }

        // After all transitions, move to the final destination map if needed
        return moveCharacterToCell(destinationMap.x, destinationMap.y, currentCharacter)
    }
}
