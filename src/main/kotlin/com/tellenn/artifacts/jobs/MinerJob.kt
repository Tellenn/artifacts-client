package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.AppConfig.maxLevel
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.services.BankService
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
 * Job implementation for characters with the "miner" job.
 */
@Component
class MinerJob(
    mapService: MapService,
    applicationContext: ApplicationContext,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    private val resourceService: ResourceService,
    private val gatheringService: GatheringService,
    private val itemService: ItemService,
    private val taskService: TaskService
) : GenericJob(mapService, applicationContext, movementService, bankService, characterService) {

    lateinit var character: ArtifactsCharacter
    val skill = "mining"

    fun run(initCharacter: ArtifactsCharacter) {

        sleep(3000)
        character = init(initCharacter)


        do{
            val itemsToCraft = ArrayList<SimpleItem>()
            val gatheringItems = itemService.getAllCraftBySkillUnderLevel(skill, character.miningLevel).filter { it.subtype != "precious_stone" }
            for(it in gatheringItems){
                if(!bankService.isInBank(it.code, 200)){
                    itemsToCraft.add(SimpleItem(it.code, (character.inventoryMaxItems - 20) / itemService.getInvSizeToCraft(it) ))
                    break
                 }
            }

            // Do some stock for the crafter
            if(itemsToCraft.isNotEmpty()){
                itemsToCraft.forEach {
                    character = gatheringService.craftOrGather(character, it.code, it.quantity)
                    character = bankService.emptyInventory(character)
                }
                continue
                // Otherwise levelup
            }else if(character.miningLevel < maxLevel){
                val item =
                    itemService.getBestCraftableItemsBySkillAndSubtypeAndMaxLevel(skill, "bar", character.miningLevel)
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
