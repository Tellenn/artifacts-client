package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.BattleClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

@Service
class BattleService(private val characterService: CharacterService, private val battleClient: BattleClient) {

    private val log = LogManager.getLogger(GatheringService::class.java)


    fun battleUntilInvIsFull(character: ArtifactsCharacter): ArtifactsCharacter{
        var currentCharacter = character

        log.debug("Character ${character.name} starting to gather resource until inventory full")

        // Continue gathering until inventory is full
        while (!characterService.isInventoryFull(currentCharacter)) {
            try {
                val response = battleClient.fight(currentCharacter.name)

                // Update character with the latest data
                currentCharacter = response.data.character

                log.debug("Character ${currentCharacter.name} gathered resource, inventory: ${characterService.countInventoryItems(currentCharacter)}/${currentCharacter.inventoryMaxItems}")

                // If no items were gathered, break the loop
                // TODO : Handle if the character is dead
                if (response.data.fight?.result.equals("loss")) {
                    characterService.rest(currentCharacter)
                    log.error("Character ${currentCharacter.name} lost the fight, resting...")
                    break
                }
                // TODO : If you have fish equipped, take it
                if(currentCharacter.hp * 2 < currentCharacter.maxHp){
                    log.debug("Character ${currentCharacter.name} is wounded, resting...")
                    characterService.rest(currentCharacter)
                }
            } catch (e: Exception) {
                log.error("Error while gathering: ${e.message}")
                break
            }
        }

        log.info("Character ${currentCharacter.name} finished fighting, inventory is now full or fighting failed")
        return currentCharacter
    }
}