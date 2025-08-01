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
import jdk.jshell.spi.ExecutionControl
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * Job implementation for characters with the "woodworker" job.
 */
@Component
class WoodworkerJob(
    mapService: MapService,
    applicationContext: ApplicationContext,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    val resourceService: ResourceService,
    val gatheringService: GatheringService,
    private val itemService: ItemService,
) : GenericJob(mapService, applicationContext, movementService, bankService, characterService) {

    lateinit var character: ArtifactsCharacter
    val skill: String = "woodcutting"

    fun run(initCharacter: ArtifactsCharacter) {
        character = init(initCharacter)

        do{
            val itemsToCraft = ArrayList<SimpleItem>()
            itemService.getAllCraftBySkillUnderLevel(skill, character.woodcuttingLevel).forEach {
                if(!bankService.isInBank(it.code, 200)){
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
            }else if(character.woodcuttingLevel < maxLevel){
                val item =
                    itemService.getBestCraftableItemsBySkillAndSubtypeAndMaxLevel(skill, "plank", character.woodcuttingLevel)
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
                log.error("Should not reach this code")
            // TODO : Tasks or monster grind ?
            }

        }while(true)
    }
}
