package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.GatheringClient
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
    private val taskclient: TaskClient
) {
    private val log = LogManager.getLogger(TaskService::class.java)

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
        // First check in bank
        var newCharacter = character
        val item = character.task;
        val quantityLeft = character.taskTotal - character.taskProgress

        if(bankService.isInBank(item, quantityLeft)){
            newCharacter = bankService.moveToBank(newCharacter)
            newCharacter = bankService.fetchItems(item, quantityLeft, newCharacter)
        }else{

        }

        newCharacter = movementService.moveCharacterToMaster("items", newCharacter)
        newCharacter = taskClient.giveItem(item, quantityLeft, newCharacter)
        newCharacter = taskClient.finishQuest(character)
        // Else check levels are ok

        // Then do what's necessary
    }
}
