package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.TaskClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.exceptions.BattleLostException
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
    private val mapService: MapService
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
            throw IllegalStateException("Character ${character.name} already has a task")
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

        movementService.moveCharacterToMaster("monsters", character)
        return taskClient.abandonTask(character.name).data.character
    }

    fun doCharacterTask(character: ArtifactsCharacter): ArtifactsCharacter {
        log.info("Character ${character.name} is doing a task")
        if(!character.task.isNullOrEmpty()){
            val newCharacter = when(character.taskType){
                "items" -> completeItemTask(character)
                "monsters" -> completeMonsterTask(character)
                else -> character
            }
            return newCharacter
        }
        character.taskType
        return gatheringClient.gather(character.name).data.character
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

        // TODO : What to do with the coins
        return newCharacter
    }

    fun completeMonsterTask(character: ArtifactsCharacter) : ArtifactsCharacter{
        var newCharacter = character
        val monsterCode = character.task
        if(monsterCode.isNullOrBlank()){
            return newCharacter
        }
        val monsterMap = mapService.findClosestMap(character, contentCode = monsterCode)
        var quantityLeft = character.taskTotal - character.taskProgress

        // TODO Check that you can actually beat the enemy

        newCharacter = equipmentService.equipBestAvailableEquipmentForMonsterInBank(newCharacter, monsterCode)
        movementService.moveCharacterToCell(monsterMap.x, monsterMap.y, newCharacter)
        try {
            while(quantityLeft > 0) {
                newCharacter = battleService.battle(newCharacter)
                quantityLeft--
                if (characterService.isInventoryFull(newCharacter)){
                    newCharacter = bankService.moveToBank(newCharacter)
                    newCharacter = bankService.emptyInventory(newCharacter)
                }
            }
        }catch (e: BattleLostException){
            log.debug("Monster in the task is too hard, stopping")
            // TODO : Something more complex before giving up ?
        }

        // TODO : What to do with the coins
        return character
    }
}
