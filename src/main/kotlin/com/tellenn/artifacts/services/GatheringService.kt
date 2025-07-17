package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.ItemClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.ItemDetails
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
    private val gatheringClient: GatheringClient,
    private val itemClient: ItemClient,
    private val mapProximityService: MapProximityService,
    private val movementService: MovementService,
    private val bankService: BankService
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
                if (response.data.details.items.isNullOrEmpty()) {
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

    fun craftOrGather(character: ArtifactsCharacter, itemCode: String, quantity: Int) : ArtifactsCharacter{
        val item = itemClient.getItemDetails(itemCode).data

        // TODO : Check in bank first ?

        if(bankService.isInBank(item.code, quantity)){
            bankService.moveToBank(character)
            return bankService.fetchItems(item.code, quantity, character)
        }

        if(item.craft == null){
            // If there is no craft, we gather
            return gather(character, item, quantity)
        }else{
            // Otherwise we craft (and call the same function for it)
            var newCharacter = character
            for( i in item.craft.items){
                newCharacter = craftOrGather(newCharacter, i.code, i.quantity*quantity)
            }
            return craft(newCharacter, item, quantity)
        }
    }

    private fun gather(character: ArtifactsCharacter, item: ItemDetails, quantity: Int) : ArtifactsCharacter{
        val levelToGather = item.level
        val skillLevel = when (item.subtype) {
            "mining" -> character.miningLevel
            "woodcutting" -> character.woodcuttingLevel
            "fishing" -> character.fishingLevel
            "alchemy" -> character.alchemyLevel
            else -> throw IllegalArgumentException("Invalid item subtype: ${item.subtype}")
        }
        if(levelToGather < skillLevel){
            // TODO : Insufficient level, should make a request or throw error ?
            throw IllegalArgumentException("Insufficient level to gather ${item.code}")
        }else{
            val mapData = mapProximityService.findClosestMap(character = character, contentCode = item.code)
            movementService.moveCharacterToCell(mapData.x, mapData.y, character)

            // TODO : Handle max inv size
            for (i in 1..quantity - 1) {
                gatheringClient.gather(characterName = character.name).data.character
            }
            return gatheringClient.gather(characterName = character.name).data.character
        }
    }

    private fun craft(character: ArtifactsCharacter,item: ItemDetails,quantity: Int) : ArtifactsCharacter {

        // TODO do the craft

        return character
    }
}
