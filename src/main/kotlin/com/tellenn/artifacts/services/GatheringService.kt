package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.clients.CraftingClient
import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.ItemClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.ItemDetails
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.GatheringResponseBody
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
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
    private val bankService: BankService,
    private val characterService: CharacterService,
    private val itemRepository: ItemRepository,
    private val characterClient: CharacterClient,
    private val craftingClient: CraftingClient,
    private val resourceService: ResourceService
) {
    private val log = LogManager.getLogger(GatheringService::class.java)

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
        while (!characterService.isInventoryFull(currentCharacter)) {
            try {
                currentCharacter = gatheringClient.gather(currentCharacter.name).data.character
                log.debug("Character ${currentCharacter.name} gathered resource, inventory: ${characterService.countInventoryItems(currentCharacter)}/${currentCharacter.inventoryMaxItems}")

            } catch (e: Exception) {
                log.error("Error while gathering: ${e.message}")
                break
            }
        }

        log.info("Character ${currentCharacter.name} finished gathering, inventory is now full or gathering failed")
        return currentCharacter
    }



    fun craftOrGather(character: ArtifactsCharacter, itemCode: String, quantity: Int, level: Int = 0, allowFight: Boolean = false) : ArtifactsCharacter{
        val item = itemClient.getItemDetails(itemCode).data

        if(level > 0 && bankService.isInBank(item.code, quantity)){
            bankService.moveToBank(character)
            return bankService.withdrawOne(item.code, quantity, character)
        }
        if(item.subtype == "mob"){
            if(allowFight){
                // TODO : fight or train
            }else{
                throw IllegalArgumentException("Cannot gather mob without fighting enabled")
            }
        }
        if(item.craft == null){
            // If there is no craft, we gather
            return gather(character, item, quantity)
        }else{
            // Otherwise we craft (and call the same function for it)
            var newCharacter = character
            for( i in item.craft.items){
                newCharacter = craftOrGather(newCharacter, i.code, i.quantity*quantity, level + 1)
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
        if(levelToGather > skillLevel){
            // TODO : Insufficient level, should make a request or throw error ?
            throw IllegalArgumentException("Insufficient level to gather ${item.code}")
        }else{
            val mapData = mapProximityService.findClosestMap(character = character, contentCode = resourceService.findResourceContaining(item.code).code)
            movementService.moveCharacterToCell(mapData.x, mapData.y, character)

            // TODO : Handle max inv size
            for (i in 1..quantity - 1) {
                gatheringClient.gather(characterName = character.name).data.character
            }
            return gatheringClient.gather(characterName = character.name).data.character
        }
    }

    private fun craft(character: ArtifactsCharacter, item: ItemDetails, quantity: Int) : ArtifactsCharacter {
        // TODO : Check in the inventory if you have what you need. For now we assume you do

        val skill = item.craft?.skill
        val mapData = mapProximityService.findClosestMap(character = character, contentCode = skill)
        var newCharacter = movementService.moveCharacterToCell(mapData.x, mapData.y, character)
        newCharacter = craftingClient.craft(newCharacter.name, item.code, quantity).data.character

        return newCharacter
    }
}
