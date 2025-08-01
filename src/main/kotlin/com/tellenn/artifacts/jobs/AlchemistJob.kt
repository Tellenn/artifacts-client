package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.AppConfig.maxLevel
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
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

/**
 * Job implementation for characters with the "alchemist" job.
 */
@Component
class AlchemistJob(
    mapService: MapService,
    applicationContext: ApplicationContext,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    private val gatheringService: GatheringService,
    private val itemService: ItemService,
    private val taskService: TaskService
) : GenericJob(mapService, applicationContext, movementService, bankService, characterService) {

    lateinit var character: ArtifactsCharacter
    val skill = "alchemy"

    fun run(initCharacter: ArtifactsCharacter) {
        character = init(initCharacter)

        do{

            // TODO : Handle other kind of potions as well
            val itemsToCraft = ArrayList<SimpleItem>()
            getHealingPotions().forEach {
                if(!bankService.isInBank(it.code, 400)){
                    itemsToCraft.add(SimpleItem(it.code, (character.inventoryMaxItems - 10) / itemService.getInvSizeToCraft(it) ))
                }
            }

            // Do some stock for the crafter
            if(itemsToCraft.isNotEmpty()){
                itemsToCraft.forEach {
                    character = gatheringService.craftOrGather(character, it.code, it.quantity)
                    character = bankService.emptyInventory(character)
                }
                // Otherwise levelup
            }else if(character.alchemyLevel < maxLevel){
                val item =
                    itemService.getBestCraftableItemsBySkillAndSubtypeAndMaxLevel(skill, "potion", character.alchemyLevel)
                if (item == null) {
                    throw Exception("No craftable item found")
                }
                character = gatheringService.craftOrGather(
                    character = character,
                    itemCode = item.code,
                    quantity = (character.inventoryMaxItems -10 )/ itemService.getInvSizeToCraft(item),
                    allowFight = true

                )
                character = bankService.emptyInventory(character)

                // Or do some tasks to get task coins
            }else{
                character = taskService.getNewItemTask(character)
                character = taskService.doCharacterTask(character)
            }

        }while(true)
    }

    private fun getHealingPotions(): List<ItemDetails>{
        val potions = itemService.getAllCraftableItemsBySkillAndSubtypeAndMaxLevel("alchemy", "potion", character.alchemyLevel)
        return potions.filter { it.effects?.none { effect -> effect.code != "restore" } ?: false }
    }
}
