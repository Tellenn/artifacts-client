package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.GatheringService
import com.tellenn.artifacts.services.ItemService
import com.tellenn.artifacts.services.MapProximityService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.ResourceService
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * Job implementation for characters with the "woodworker" job.
 */
@Component
class WoodworkerJob(
    mapProximityService: MapProximityService,
    applicationContext: ApplicationContext,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    val resourceService: ResourceService,
    val gatheringService: GatheringService,
    private val itemService: ItemService,
) : GenericJob(mapProximityService, applicationContext, movementService, bankService, characterService) {

    lateinit var character: ArtifactsCharacter

    fun run(initCharacter: ArtifactsCharacter) {
        character = init(initCharacter)

        do{
            val item = itemService.getBestCraftableItemsBySkillAndSubtypeAndMaxLevel("woodcutting","plank",character.woodcuttingLevel)
            if(item == null){
                throw Exception("No craftable item found")
            }
            character = gatheringService.craftOrGather(character, item.code, character.inventoryMaxItems / itemService.getInvSizeToCraft(item))
            character = bankService.emptyInventory(character)
        }while(true)
    }
}
