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
import com.tellenn.artifacts.services.sync.BankItemSyncService
import jdk.jshell.spi.ExecutionControl
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import java.lang.Thread.sleep
import kotlin.text.isNullOrEmpty

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
    val itemService: ItemService,
    val bankItemSyncService: BankItemSyncService
) : GenericJob(mapService, movementService, bankService, characterService, accountClient, taskService) {

    lateinit var character: ArtifactsCharacter
    val skill: String = "woodcutting"

    fun run(characterName: String) {
        sleep(4000)
        character = init(characterName)
        character = craftBasicMaterialFromBank(skill, "plank", itemService, gatheringService, bankItemSyncService)


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
                    log.info("${character.name} is stocking up on some ${it.code}")
                    character = gatheringService.craftOrGather(character, it.code, it.quantity)
                    character = bankService.emptyInventory(character)
                }
                continue
                // Otherwise levelup
            }else if(character.woodcuttingLevel < maxLevel){
                log.info("${character.name} is leveling his woodcutting skill")
                val items =
                    itemService.getAllCraftableItemsBySkillAndSubtypeAndMaxLevel(skill, "plank", character.woodcuttingLevel)
                        .filter { it.code != "cursed_plank" && it.code != "magical_plank" }
                if (items.isEmpty()) {
                    throw Exception("No craftable item found")
                }
                val item = items.first()
                character = gatheringService.craftOrGather(
                    character,
                    item.code,
                    (character.inventoryMaxItems -10 )/ itemService.getInvSizeToCraft(item)
                )
                character = bankService.emptyInventory(character)

                // Or do some tasks to get task coins
            }else{
                log.info("${character.name} is doing a new itemTask")
                if(character.task.isNullOrEmpty()){
                    character = taskService.getNewItemTask(character)
                }
                character = taskService.doCharacterTask(character)
            }
        }while(true)
    }
}
