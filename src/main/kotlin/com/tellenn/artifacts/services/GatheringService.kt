package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.GatheringResponseBody
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

/**
 * Service for managing gathering operations.
 * Provides functionality for gathering resources with inventory management.
 */
@Service
class GatheringService(
    private val gatheringClient: GatheringClient
) {
    private val log = LogManager.getLogger(GatheringService::class.java)

    /**
     * Checks if the character has available inventory slots and gathers a resource.
     * If the inventory is full, no gathering is performed.
     *
     * @param character The character that will gather
     * @param resourceCode The code of the resource to gather
     * @return The updated character if gathering was successful, or the original character if inventory is full
     */
    fun gatherIfInventoryAvailable(character: ArtifactsCharacter): ArtifactsResponseBody<GatheringResponseBody> {
        // Check if character has available inventory slots
        if (isInventoryFull(character)) {
            log.info("Character ${character.name} has a full inventory, cannot gather")
            throw IllegalStateException("Cannot gather with a full inventory")
        }

        // Character has space, perform gathering
        log.info("Character ${character.name} is gathering resource")
        return gatheringClient.gather(character.name)
    }

    /**
     * Gathers a resource repeatedly until the character's inventory is full.
     *
     * @param character The character that will gather
     * @param resourceCode The code of the resource to gather
     * @return The updated character with a full inventory
     */
    fun gatherUntilInventoryFull(character: ArtifactsCharacter): ArtifactsCharacter {
        var currentCharacter = character

        log.debug("Character ${character.name} starting to gather resource until inventory full")

        // Continue gathering until inventory is full
        while (!isInventoryFull(currentCharacter)) {
            try {
                val response = gatheringClient.gather(currentCharacter.name)

                // Update character with the latest data
                currentCharacter = response.data.character

                log.debug("Character ${currentCharacter.name} gathered resource, inventory: ${countInventoryItems(currentCharacter)}/${currentCharacter.inventoryMaxItems}")

                // If no items were gathered, break the loop
                if (response.data.gathered.isNullOrEmpty()) {
                    log.debug("Gathering stopped: No items gathered")
                    break
                }
            } catch (e: Exception) {
                log.error("Error while gathering: ${e.message}")
                break
            }
        }

        log.info("Character ${currentCharacter.name} finished gathering, inventory is now full or gathering failed")
        return currentCharacter
    }

    /**
     * Checks if a character's inventory is full.
     *
     * @param character The character to check
     * @return true if the inventory is full, false otherwise
     */
    private fun isInventoryFull(character: ArtifactsCharacter): Boolean {
        val inventoryCount = countInventoryItems(character)
        return inventoryCount >= character.inventoryMaxItems
    }

    /**
     * Counts the number of items in a character's inventory.
     *
     * @param character The character whose inventory to count
     * @return The number of items in the inventory
     */
    private fun countInventoryItems(character: ArtifactsCharacter): Int {
        return character.inventory?.size ?: 0
    }
}
