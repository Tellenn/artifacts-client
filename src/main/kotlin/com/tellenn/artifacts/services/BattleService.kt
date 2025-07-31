package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.BattleClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.ItemDetails
import com.tellenn.artifacts.clients.models.MonsterData
import com.tellenn.artifacts.exceptions.BattleFailedException
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

@Service
class BattleService(
    private val characterService: CharacterService,
    private val battleClient: BattleClient,
    private val monsterService: MonsterService,
    private val mapProximityService: MapProximityService,
    private val movementService: MovementService
) {

    private val log = LogManager.getLogger(GatheringService::class.java)


    fun battleUntilInvIsFull(character: ArtifactsCharacter): ArtifactsCharacter{
        var currentCharacter = character

        log.debug("Character ${character.name} starting to gather resource until inventory full")

        // Continue gathering until inventory is full
        while (!characterService.isInventoryFull(currentCharacter)) {
            try {
                currentCharacter = battle(currentCharacter)
            } catch (e: Exception) {
                log.error("Error while gathering: ${e.message}")
                break
            }
        }

        log.info("Character ${currentCharacter.name} finished fighting, inventory is now full or fighting failed")
        return currentCharacter
    }

    fun fightToGetItem(character: ArtifactsCharacter, itemCode: String, quantity: Int, shouldTrain: Boolean = false): ArtifactsCharacter {
        val monster = monsterService.findMonsterThatDrop(itemCode)
        val map = mapProximityService.findClosestMap(character, contentCode = monster?.code)
        var newCharacter = movementService.moveCharacterToCell(map.x, map.y, character)
        try {
            while (!characterService.has(newCharacter, quantity, itemCode)){
                // TODO : add protection for full inventory
                newCharacter = battle(newCharacter)

            }
        }catch (e : BattleFailedException){
            if(shouldTrain){
                newCharacter = train(newCharacter, -1)
                return fightToGetItem(newCharacter, itemCode, quantity, false)
            }
        }
        return newCharacter
    }

    fun train(character: ArtifactsCharacter, penalty: Int) : ArtifactsCharacter{
        val monster = monsterService.findStrongestMonsterUnderLevel(character.level + penalty)
        val mapData = mapProximityService.findClosestMap(character, contentCode = monster.code)

        var newCharacter = movementService.moveCharacterToCell(mapData.x, mapData.y, character)
        try {
            while (character.level == newCharacter.level){
                newCharacter = battle(newCharacter)
            }
        }catch (e : BattleFailedException){
            train(character, penalty-1)
        }
        return newCharacter
    }

    fun battle(character: ArtifactsCharacter) : ArtifactsCharacter{
        var currentCharacter = character
        val response = battleClient.fight(currentCharacter.name)

        // Update character with the latest data
        currentCharacter = response.data.character

        log.debug("Character ${currentCharacter.name} gathered resource, inventory: ${characterService.countInventoryItems(currentCharacter)}/${currentCharacter.inventoryMaxItems}")

        // If no items were gathered, break the loop
        // TODO : Handle if the character is dead
        if (response.data.fight?.result.equals("loss")) {
            currentCharacter = characterService.rest(currentCharacter)
            log.error("Character ${currentCharacter.name} lost the fight, resting...")
            throw BattleFailedException()
        }
        // TODO : If you have fish equipped, take it
        if(currentCharacter.hp * 2 < currentCharacter.maxHp){
            log.debug("Character ${currentCharacter.name} is wounded, resting...")
            currentCharacter = characterService.rest(currentCharacter)
        }

        return currentCharacter
    }
}