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
import java.lang.Thread.sleep

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
        sleep(1000)
        character = init(initCharacter)
        do {
            val skillToLevel = getLowestSkillLevel(character)
            val itemsToCraft = getListOfItemToCraftUnderLevel(
                character,
                listOf("weaponcrafting","gearcrafting","jewelrycrafting")
            )


            if (!itemsToCraft.isEmpty()) {
                for (itemDetail in itemsToCraft) {
                    character = gatheringService.craftOrGather(character, itemDetail.code, 1, allowFight = true)
                    character = bankService.emptyInventory(character)
                    saveOrUpdateCraftedItem(itemDetail)
                }
            } else {

                val itemToCraft =
                    getTopLowestCostingItemForLevel(character.getLevelOf(skillToLevel), listOf(skillToLevel))
                val oldLevel = character.getLevelOf(skillToLevel)
                while (oldLevel == character.getLevelOf(skillToLevel)) {
                    character = gatheringService.craftOrGather(character, itemToCraft.code, 1, allowFight = true)
                    character = gatheringService.recycle(character, itemToCraft, 1)
                    character = bankService.emptyInventory(character)
                }
            }
        }while (true)
    }

    private fun getLowestSkillLevel(character: ArtifactsCharacter): String{
        val skills = mapOf(
            "weaponcrafting"  to (character.weaponcraftingLevel  - character.weaponcraftingLevel  % 5),
            "gearcrafting"    to (character.gearcraftingLevel    - character.gearcraftingLevel    % 5),
            "jewelrycrafting" to (character.jewelrycraftingLevel - character.jewelrycraftingLevel % 5),
        )
        return skills.minWith(compareBy { it.value }).key
    }

    private fun getListOfItemToCraftUnderLevel(character : ArtifactsCharacter, skills : List<String>) : List<ItemDetails>{
        val items = ArrayList<ItemDetails>()


        for (skill in skills) {
            val minLevel = character.getLevelOf(skill) / 5 * 5
            items.addAll(itemService.getCrafterItemsBetweenLevel(minLevel-1, character.getLevelOf(skill) +1, skills))
        }
        // Based on crafted history
        val alreadyCraftedItem = craftedItemRepository.findAllByQuantityLessThan(3).map { it.code }

        // Or based on available bank items ?
        val availableCraftedItem = bankService.getAllEquipmentsUnderLevel(50).map { it.code }

        // TODO : Improve based on rare craft ?

        return items.filter { !availableCraftedItem.contains(it.code) }.sortedBy { it.level }
    }

    /**
     * Saves a new crafted item or updates its quantity if it already exists
     */
    private fun saveOrUpdateCraftedItem(itemDetail: ItemDetails) {
        val existingItem = craftedItemRepository.findById(itemDetail.code)
        if (existingItem.isPresent) {
            val updatedItem = existingItem.get().copy(quantity = existingItem.get().quantity + 1)
            craftedItemRepository.save(updatedItem)
        } else {
            craftedItemRepository.save(CraftedItemDocument.fromItemDetails(itemDetail, 1))
        }
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
