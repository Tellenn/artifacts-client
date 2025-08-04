package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for handling character movement.
 * Provides methods to move a character to a specific cell.
 */
@Service
class MovementService(
    private val movementClient: MovementClient,
    private val mapService: MapService
) {
    private val logger = LoggerFactory.getLogger(MovementService::class.java)

    /**
     * Moves a character to a specific cell.
     * If the character is already at the destination, no movement is performed.
     *
     * @param characterName The name of the character to move
     * @param x The x-coordinate of the destination
     * @param y The y-coordinate of the destination
     * @param character The character object (optional). If provided, it's used to check if the character is already at the destination
     * @return The updated character object
     */
    fun moveCharacterToCell(x: Int, y: Int, character: ArtifactsCharacter): ArtifactsCharacter {
        // If character is provided and already at the destination, skip the API call
        if (character.x == x && character.y == y) {
            logger.debug("Character ${character.name} is already at position ($x, $y), skipping movement call")
            return character
        }

        return movementClient.makeMovementCall(character.name, x, y).data.character
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
}
