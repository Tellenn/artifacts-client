package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.TaskClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.GatheringResponseBody
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils

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
    private val gatheringService: GatheringService
) {
    private val log = LogManager.getLogger(TaskService::class.java)

    // TODO : function to get a quest


    // TODO : function to reset a quest

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
        val item = character.task
        if(item.isNullOrBlank()){
            return newCharacter
        }
        val quantityLeft = character.taskTotal - character.taskProgress

        gatheringService.craftOrGather(character, item, quantityLeft)


        newCharacter = movementService.moveCharacterToMaster("items", newCharacter)

        // TODO : Handle tasks that have more than 100 items (meaning you have to trade multiple times)
        newCharacter = taskClient.giveItem(newCharacter.name, item, quantityLeft).data.character
        newCharacter = taskClient.completeTask(newCharacter.name).data.character
        return newCharacter
    }

    fun completeMonsterTask(character: ArtifactsCharacter) : ArtifactsCharacter{
        var newCharacter = character
        val monsterCode = character.task
        if(monsterCode.isNullOrBlank()){
            return newCharacter
        }
        val quantityLeft = character.taskTotal - character.taskProgress

        // TODO Check that you can actually beat the enemy

        // TODO : Equip items

        // TODO : fight X enemies with a battleService

        return character
    }
}
