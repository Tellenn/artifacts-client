package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.MainRuntime
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.config.CharacterConfig.Companion.getPredefinedCharacters
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.GatheringService
import com.tellenn.artifacts.services.ItemService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.TaskService
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Component
import kotlin.math.min

/**
 * Abstract base class for all job types.
 * Provides common functionality and structure for job implementations.
 */
@Component
open class GenericJob(
    val mapService: MapService,
    val movementService: MovementService,
    val bankService: BankService,
    val characterService: CharacterService,
    val accountClient: AccountClient,
    val taskService: TaskService
) {
    val log = LogManager.getLogger(MainRuntime::class.java)

    /**
     * Initialization for clean re-run, return ever character to the closest bank for an inventory cleanup
     * This is the main entry point for job execution.
     */
    fun init(characterName : String) : ArtifactsCharacter{
        var tempCharacter = accountClient.getCharacter(characterName).data
        tempCharacter = bankService.moveToBank(tempCharacter)
        tempCharacter = bankService.emptyInventory(tempCharacter)
        tempCharacter = characterService.rest(tempCharacter)
        return tempCharacter
    }

    fun catchBackCrafter(character: ArtifactsCharacter) : ArtifactsCharacter{
        val crafter = accountClient.getCharacter(getPredefinedCharacters().first { it.job == "crafter" }.name).data
        val lowestCraftLevel = min(min(crafter.weaponcraftingLevel, crafter.gearcraftingLevel), crafter.jewelrycraftingLevel) / 5 * 5
        var newCharacter = character
        if(newCharacter.level < lowestCraftLevel){
            log.info("Character ${newCharacter.name} need to catch back crafter : craft level is $lowestCraftLevel, character level is ${character.level}")
            while(newCharacter.level < lowestCraftLevel){
                newCharacter = taskService.getNewMonsterTask(newCharacter)
                newCharacter = taskService.doCharacterTask(newCharacter)
            }
        }
        return newCharacter
    }

    fun craftBasicMaterialFromBank(skill: String, subType: String, itemService: ItemService, gatheringService: GatheringService, bankItemSyncService: BankItemSyncService) : ArtifactsCharacter{
        var character = accountClient.getCharacter(getPredefinedCharacters().first { it.job == "crafter" }.name).data
        character = bankService.emptyInventory(character)
        itemService.getAllCraftableItemsBySkillAndSubtypeAndMaxLevel(skill, subType, character.getLevelOf(skill))
            .sortedBy { -it.level }
            .forEach {
                if(bankService.canCraftFromBank(it)){
                    var craftableAmount = character.inventoryMaxItems / itemService.getInvSizeToCraft(it)
                    it.craft?.items?.forEach { item ->
                        craftableAmount = min(craftableAmount, bankService.getOne(item.code).quantity / item.quantity)
                    }
                    // Protection, but should be un-needed
                    if(craftableAmount > 0){
                        character = gatheringService.craftOrGather(character, it.code, craftableAmount)
                    }else{
                        log.error("Tried to craft an item where I can't in fact, resyncing bank")
                        bankItemSyncService.syncAllItems()
                    }
                }
                character = bankService.emptyInventory(character)
            }
        return character
    }

}
