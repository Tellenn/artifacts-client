package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BattleClient
import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.exceptions.BattleLostException
import com.tellenn.artifacts.exceptions.CharacterInventoryFullException
import com.tellenn.artifacts.models.SimpleItem
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.min

@Service
class BattleService(
    private val characterService: CharacterService,
    private val battleClient: BattleClient,
    private val monsterService: MonsterService,
    private val mapService: MapService,
    private val movementService: MovementService,
    private val accountClient: AccountClient,
    private val equipmentService: EquipmentService,
    private val itemService: ItemService,
    private val characterClient: CharacterClient,
    private val bankService: BankService
) {

    private val log = LogManager.getLogger(GatheringService::class.java)


    fun battleUntilInvIsFull(character: ArtifactsCharacter, monsterCode: String): ArtifactsCharacter{
        var currentCharacter = character

        log.debug("Character ${character.name} starting to gather resource until inventory full")

        // Continue gathering until inventory is full
        while (!characterService.isInventoryFull(currentCharacter)) {
            try {
                currentCharacter = battle(currentCharacter, monsterCode)
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
        if(monster == null){
            log.error("Monster with code $itemCode not found")
            return character
        }
        val map = mapService.findClosestMap(character, contentCode = monster.code)
        var newCharacter = equipmentService.equipBestAvailableEquipmentForMonsterInBank(character, monster.code)
        newCharacter = movementService.moveCharacterToCell(map.x, map.y, newCharacter)
        try {
            while (!characterService.has(newCharacter, quantity, itemCode)){
                try {
                    newCharacter = battle(newCharacter, monster.code)
                }catch (e : CharacterInventoryFullException){
                    newCharacter = bankService.moveToBank(newCharacter)
                    newCharacter = bankService.emptyInventory(newCharacter)
                    newCharacter = movementService.moveCharacterToCell(map.x, map.y, newCharacter)
                }
            }
        }catch (e : BattleLostException){
            newCharacter = accountClient.getCharacter(newCharacter.name).data
            if(shouldTrain){
                newCharacter = train(newCharacter, -1)
                return fightToGetItem(newCharacter, itemCode, quantity, shouldTrain)
            }
        }
        return newCharacter
    }

    fun train(character: ArtifactsCharacter, penalty: Int) : ArtifactsCharacter{
        val monster = monsterService.findStrongestMonsterUnderLevel(character.level + penalty)
        val mapData = mapService.findClosestMap(character, contentCode = monster.code)
        var newCharacter = equipmentService.equipBestAvailableEquipmentForMonsterInBank(character, monster.code)

        newCharacter = movementService.moveCharacterToCell(mapData.x, mapData.y, newCharacter)
        try {
            while (character.level == newCharacter.level){
                newCharacter = battle(newCharacter, monster.code)
            }
        }catch (e : BattleLostException){
            newCharacter = accountClient.getCharacter(newCharacter.name).data
            newCharacter = train(newCharacter, penalty-1)
        }
        return newCharacter
    }

    fun battle(character: ArtifactsCharacter, monsterCode: String) : ArtifactsCharacter{
        var currentCharacter = character
        val response = battleClient.fight(currentCharacter.name)

        // Update character with the latest data
        currentCharacter = response.data.character
        log.debug("Character ${currentCharacter.name} gathered resource, inventory: ${characterService.countInventoryItems(currentCharacter)}/${currentCharacter.inventoryMaxItems}")

        if(currentCharacter.hp * 2 < (currentCharacter.maxHp * 1.1)){
            log.debug("Character ${currentCharacter.name} is wounded, resting...")
            val ownedHealingItems = itemService.getHealingItems(character.inventory.map { SimpleItem(it.code, it.quantity) })
            if(ownedHealingItems.isNotEmpty()){
                val item = ownedHealingItems.map { item -> itemService.getItem(item.code) }.minBy { it.level }
                val owned = ownedHealingItems.find { it.code == item.code }?.quantity ?: 1
                val missingHealth = currentCharacter.maxHp - currentCharacter.hp
                val healingValue = item.effects?.first { it.code == "heal" }?.value ?: 1
                val numberToEat = missingHealth / healingValue
                currentCharacter = characterClient.useItem(currentCharacter.name, item.code, min(max(1,numberToEat), owned)).data.character
            }
            // If still wounded or still haven't eaten
            if(currentCharacter.hp < currentCharacter.maxHp ){
                currentCharacter = characterService.rest(currentCharacter)
            }

            if(response.data.fight?.result.equals("loss")){
                // If we ran out of potion during the fight, then we try to fetch some more
                if((character.utility1Slot != "" && currentCharacter.utility1Slot == "" )||
                   (character.utility2Slot != "" && currentCharacter.utility2Slot == "" )){
                    currentCharacter = equipmentService.equipBestPotionsForFight(currentCharacter, monsterCode)
                    return battle(currentCharacter, monsterCode)
                }else{
                    // We lost because the fight is too tough or bad luck
                    throw BattleLostException(monsterCode)
                }
            }
        }

        return currentCharacter
    }
}