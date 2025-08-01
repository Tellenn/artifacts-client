package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.db.documents.CraftedItemDocument
import com.tellenn.artifacts.db.repositories.CraftedItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.GatheringService
import com.tellenn.artifacts.services.ItemService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.MovementService
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * Job implementation for characters with the "crafter" job.
 */
@Component
class CrafterJob(
    mapService: MapService,
    applicationContext: ApplicationContext,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    private val itemService: ItemService,
    private val craftedItemRepository: CraftedItemRepository,
    private val gatheringService: GatheringService
) : GenericJob(mapService, applicationContext, movementService, bankService, characterService) {

    lateinit var character: ArtifactsCharacter

    fun run(initCharacter: ArtifactsCharacter) {
        character = init(initCharacter)
        val skillToLevel = getLowestSkillLevel(character)
        val itemsToCraft = getListOfItemToCraftUnderLevel(character.getLevelOf(skillToLevel))

        for(itemDetail in itemsToCraft){
            character = gatheringService.craftOrGather(character, itemDetail.code, 1)
            character = bankService.emptyInventory(character)
        }


    }

    private fun getLowestSkillLevel(character: ArtifactsCharacter): String{
        val skills = mapOf(character.weaponcraftingLevel to "weaponcrafting", character.gearcraftingLevel to "gearcrafting", character.jewelrycraftingLevel to "jewelrycrafting")
        return skills.minBy { it.value }.value
    }

    private fun getListOfItemToCraftUnderLevel(level: Int) : List<ItemDetails>{
        val skills = listOf<String>("weaponcrafting", "gearcrafting", "jewelrycrafting")
        val minLevel = level - level % 5

        val items = itemService.getCrafterItemsBetweenLevel(level, minLevel, skills)

        val alreadyCraftedItem = craftedItemRepository.findByLevelBetween(level, minLevel).map { it.code }

        return items.filter { !alreadyCraftedItem.contains(it.code) }
    }
}
