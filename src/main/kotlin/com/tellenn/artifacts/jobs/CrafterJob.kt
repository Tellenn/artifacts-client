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
        val itemsToCraft = getListOfItemToCraftUnderLevel(character.getLevelOf(skillToLevel), listOf(skillToLevel))


        if(!itemsToCraft.isEmpty()){
            for(itemDetail in itemsToCraft){
                character = gatheringService.craftOrGather(character, itemDetail.code, 1, allowFight = true)
                character = bankService.emptyInventory(character)
                craftedItemRepository.save(CraftedItemDocument.fromItemDetails(itemDetail, 1))
            }
        }else{
            val itemToCraft = getTopLowestCostingItemForLevel(character.getLevelOf(skillToLevel), listOf(skillToLevel))
            val oldLevel = character.getLevelOf(skillToLevel)
            while (oldLevel == character.getLevelOf(skillToLevel)){
                character = gatheringService.craftOrGather(character, itemToCraft.code, 1)
            }
        }
    }

    private fun getLowestSkillLevel(character: ArtifactsCharacter): String{
        val skills = mapOf(
            "weaponcrafting"  to (character.weaponcraftingLevel  - character.weaponcraftingLevel  % 5),
            "gearcrafting"    to (character.gearcraftingLevel    - character.gearcraftingLevel    % 5),
            "jewelrycrafting" to (character.jewelrycraftingLevel - character.jewelrycraftingLevel % 5),
        )
        return skills.minWith(compareBy { it.value }).key
    }

    private fun getListOfItemToCraftUnderLevel(level: Int, skills : List<String>) : List<ItemDetails>{
        val minLevel = level / 5 * 5

        val items = itemService.getCrafterItemsBetweenLevel(minLevel-1, level +1, skills)

        val alreadyCraftedItem = craftedItemRepository.findByLevelBetween(minLevel-1, level +1).map { it.code }

        return items.filter { !alreadyCraftedItem.contains(it.code) }
    }

    private fun getTopLowestCostingItemForLevel(level: Int, skills : List<String>) : ItemDetails{
        val forbiddenItemCode = listOf("magical_cure", "jasper_crystal", "astralyte_crystal", "enchanted_fabric", "ruby", "sapphire", "emerald", "topaz", "diamond")
        val minLevel = level / 5 * 5
        val items = itemService.getCrafterItemsBetweenLevel(minLevel-1, level +1, skills)
        val itemCostMatrix = HashMap<ItemDetails, Int>()
        // Exclude very hard items and the tutorial one
        items.filter { !forbiddenItemCode.contains(it.code) && it.code != "wooden_staff"  }
            .forEach {
            itemCostMatrix.put(
                it,
                itemService.getInvSizeToCraft(it))
                // TODO : Do a better math for this
        }
        return itemCostMatrix.minWith(compareBy { it.value }).key
    }
}
