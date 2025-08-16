package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

/**
 * Service for managing character-related operations.
 * Provides functionality for character resting and other character-related actions.
 */
@Service
class CharacterService(
    private val characterClient: CharacterClient
) {
    private val log = LogManager.getLogger(CharacterService::class.java)

    /**
     * Makes a character rest to recover HP.
     * If the character is already at full HP, no rest is performed.
     *
     * @param character The character to rest
     * @return The updated character if rested, or the original character if already at full HP
     */
    fun rest(character: ArtifactsCharacter): ArtifactsCharacter {
        // Check if character is already at full HP
        if (character.hp >= character.maxHp) {
            return character
        }

        // Character needs to rest
        val response = characterClient.rest(character.name)
        
        // Return the updated character
        return response.data.character
    }

    fun equip(character: ArtifactsCharacter, code: String, slot: String, quantity: Int): ArtifactsCharacter {
        return characterClient.equipItem(character.name, code, slot, quantity).data.character
    }

    fun unequip(character: ArtifactsCharacter, slot: String, quantity: Int): ArtifactsCharacter {
        return characterClient.unequipItem(character.name, slot, quantity).data.character
    }

    /**
     * Counts the number of items in a character's inventory.
     *
     * @param character The character whose inventory to count
     * @return The number of items in the inventory
     */
    fun countInventoryItems(character: ArtifactsCharacter): Int {
        val inventory = character.inventory
        var count = 0
        if (inventory != null) {
            for (slot in inventory){
                if(slot.code != ""){
                    count += slot.quantity
                }
            }
        }else{
            throw IllegalStateException("Character ${character.name} has no inventory")
        }
        return count
    }


    /**
     * Checks if a character's inventory is full.
     *
     * @param character The character to check
     * @return true if the inventory is full, false otherwise
     */
    fun isInventoryFull(character: ArtifactsCharacter): Boolean {
        val inventoryCount = countInventoryItems(character)
        return inventoryCount >= (character.inventoryMaxItems -5)
    }

    fun has(character: ArtifactsCharacter, quantity: Int, itemcode: String): Boolean {
        var count = 0
        character.inventory?.filter { it.code == itemcode }?.forEach { count += it.quantity }
        return count >= quantity
    }

    fun destroyAllOfOne(character: ArtifactsCharacter, code: String): ArtifactsCharacter {
        val inventory = character.inventory.firstOrNull { it.code == code }
        if(inventory != null){
             return characterClient.destroy(character.name, code, inventory.quantity).data.character
        }
        return character
    }

}