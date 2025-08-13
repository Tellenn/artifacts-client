package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.TaskClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.exceptions.BattleLostException
import com.tellenn.artifacts.exceptions.TaskFailedException
import com.tellenn.artifacts.models.SimpleItem
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

/**
 * Service for managing gathering operations.
 * Provides functionality for gathering resources with inventory management.
 */
@Service
class TaskService(
    private val gatheringClient: GatheringClient,
    private val bankService: BankService,
    private val movementService: MovementService,
    private val taskClient: TaskClient,
    private val gatheringService: GatheringService,
    private val itemService: ItemService,
    private val battleService: BattleService,
    private val characterService: CharacterService,
    private val equipmentService: EquipmentService,
    private val mapService: MapService,
    private val accountClient: AccountClient,
    private val characterClient: CharacterClient
) {
    private val log = LogManager.getLogger(TaskService::class.java)

    fun getNewItemTask(character : ArtifactsCharacter): ArtifactsCharacter{
        if(!character.taskType.isNullOrEmpty()){
            throw IllegalStateException("Character ${character.name} already has a task")
        }

        movementService.moveCharacterToMaster("items", character)
        return taskClient.acceptTask(character.name).data.character
    }

    fun getNewMonsterTask(character : ArtifactsCharacter): ArtifactsCharacter{
        if(!character.taskType.isNullOrEmpty()){
            return character
        }

        movementService.moveCharacterToMaster("monsters", character)
        return taskClient.acceptTask(character.name).data.character
    }


    fun abandonItemTask(character : ArtifactsCharacter): ArtifactsCharacter{
        if(character.taskType.isNullOrEmpty()){
            throw IllegalStateException("Character ${character.name} already has no task")
        }

        movementService.moveCharacterToMaster("items", character)
        return taskClient.abandonTask(character.name).data.character
    }

    fun abandonMonsterTask(character : ArtifactsCharacter): ArtifactsCharacter{
        if(character.taskType.isNullOrEmpty()){
            throw IllegalStateException("Character ${character.name} already has no task")
        }
        var newCharacter = bankService.moveToBank(character)
        newCharacter = bankService.withdrawOne("tasks_coin", 1, newCharacter)
        newCharacter = movementService.moveCharacterToMaster("monsters", newCharacter)
        return taskClient.abandonTask(newCharacter.name).data.character
    }

    fun doCharacterTask(character: ArtifactsCharacter): ArtifactsCharacter {
        log.debug("Character ${character.name} is doing a task")
        if(!character.task.isNullOrEmpty()){
            val newCharacter = when(character.taskType){
                "items" -> completeItemTask(character)
                "monsters" -> completeMonsterTask(character)
                else -> character
            }
            return newCharacter
        }
        return character
    }

    fun completeItemTask(character: ArtifactsCharacter) : ArtifactsCharacter{
        var newCharacter = character
        val itemCode = character.task
        if(itemCode.isNullOrBlank()){
            return newCharacter
        }
        val item = itemService.getItem(itemCode);
        var quantityLeft = newCharacter.taskTotal - character.taskProgress

        val sizeToCraft = itemService.getInvSizeToCraft(item)
        while(quantityLeft > 0){
            val quantityToCraft = Math.min(quantityLeft, (character.inventoryMaxItems - 10) / sizeToCraft )
            newCharacter = gatheringService.craftOrGather(newCharacter, itemCode, quantityToCraft)
            newCharacter = movementService.moveCharacterToMaster("items", newCharacter)
            newCharacter = taskClient.giveItem(newCharacter.name, itemCode, quantityToCraft).data.character
            newCharacter = bankService.moveToBank(newCharacter)
            quantityLeft -= quantityToCraft
        }

        newCharacter = movementService.moveCharacterToMaster("items", newCharacter)
        newCharacter = taskClient.completeTask(newCharacter.name).data.character
        newCharacter = bankService.moveToBank(newCharacter)

        return newCharacter
    }

    fun completeMonsterTask(character: ArtifactsCharacter) : ArtifactsCharacter{
        var count = 0;
        var newCharacter = character
        val monsterCode = character.task
        if(monsterCode.isNullOrBlank()){
            return newCharacter
        }
        val monsterMap = mapService.findClosestMap(character, contentCode = monsterCode)
        var quantityLeft = character.taskTotal - character.taskProgress

        // TODO : Check that you can actually beat the enemy
        if(quantityLeft > 0){
            newCharacter = equipmentService.equipBestAvailableEquipmentForMonsterInBank(newCharacter, monsterCode)
            newCharacter = movementService.moveCharacterToCell(monsterMap.x, monsterMap.y, newCharacter)
        }

        while(quantityLeft > 0 && count < 5) {
            try {
                newCharacter = battleService.battle(newCharacter, character.task!!)
                quantityLeft--
                if (characterService.isInventoryFull(newCharacter)){
                    newCharacter = bankService.moveToBank(newCharacter)
                    newCharacter = bankService.emptyInventory(newCharacter)
                }
            }catch (e: BattleLostException){
                newCharacter = accountClient.getCharacter(newCharacter.name).data
                log.debug("Monster in the task is too hard, stopping")
                // TODO : Something more complex before giving up ?
                count++
                if(count == 5){
                    throw TaskFailedException()
                }else{
                    newCharacter = movementService.moveCharacterToCell(monsterMap.x, monsterMap.y, newCharacter)
                }
            }
        }

        newCharacter = movementService.moveCharacterToMaster("monsters", newCharacter)
        newCharacter = taskClient.completeTask(newCharacter.name).data.character
        return newCharacter
    }

    fun exchangeRewardFromBank(character: ArtifactsCharacter): ArtifactsCharacter {
        var newCharacter = character
        newCharacter = bankService.moveToBank(newCharacter)
        newCharacter = bankService.withdrawOne("tasks_coin", 6, newCharacter)
        return exchangeReward(newCharacter)
    }

    fun exchangeReward(character: ArtifactsCharacter): ArtifactsCharacter{
        var newCharacter = character
        newCharacter = movementService.moveCharacterToMaster("items", newCharacter)
        val response = taskClient.gatchaReward(newCharacter.name).data
        val cash = listOf("small_bag_of_gold","bag_of_gold")
        response.rewards.items
            .filter { cash.contains(it.code) }
            .forEach { newCharacter = characterClient.useItem(character.name, it.code, 1).data.character }
        return newCharacter
    }
}
