package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.AppConfig.maxLevel
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.BattleService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.GatheringService
import com.tellenn.artifacts.services.ItemService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.ResourceService
import com.tellenn.artifacts.services.TaskService
import jdk.jshell.spi.ExecutionControl
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.lang.Thread.sleep

/**
 * Job implementation for characters with the "woodworker" job.
 */
@Component
class WoodworkerJob(
    mapService: MapService,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    accountClient: AccountClient,
    taskService: TaskService,
    val gatheringService: GatheringService,
    private val itemService: ItemService
) : GenericJob(mapService, movementService, bankService, characterService, accountClient, taskService) {

    lateinit var character: ArtifactsCharacter
    val skill: String = "woodcutting"

    fun run(characterName: String) {
        sleep(4000)
        character = init(characterName)

        do{
            character = catchBackCrafter(character)
            val itemsToCraft = ArrayList<SimpleItem>()
            val gatheringItems = itemService.getAllCraftBySkillUnderLevel(skill, character.woodcuttingLevel)
                .filter { it.subtype != "precious_stone" }
                .filter { it.code != "cursed_plank" && it.code != "magical_plank" && it.code != "magic_sap"}
            for(it in gatheringItems){
                if(!bankService.isInBank(it.code, 200)){
                    itemsToCraft.add(SimpleItem(it.code, (character.inventoryMaxItems - 20) / itemService.getInvSizeToCraft(it) ))
                    break
                }
            }

            // Do some stock for the crafter
            if(itemsToCraft.isNotEmpty()){
                for(it in itemsToCraft){
                    character = gatheringService.craftOrGather(character, it.code, it.quantity)
                    character = bankService.emptyInventory(character)
                }
                continue
                // Otherwise levelup
            }else if(character.woodcuttingLevel < maxLevel){
                val items =
                    itemService.getAllCraftableItemsBySkillAndSubtypeAndMaxLevel(skill, "plank", character.woodcuttingLevel)
                        .filter { it.code != "cursed_plank" && it.code != "magical_plank" }
                val item = items.first()
                if (item == null) {
                    throw Exception("No craftable item found")
                }
                character = gatheringService.craftOrGather(
                    character,
                    item.code,
                    (character.inventoryMaxItems -10 )/ itemService.getInvSizeToCraft(item)
                )
                character = bankService.emptyInventory(character)

                // Or do some tasks to get task coins
            }else{
                character = taskService.getNewItemTask(character)
                character = taskService.doCharacterTask(character)
            }

        }while(true)
    }
}
