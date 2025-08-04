package com.tellenn.artifacts.services

import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.SimpleItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.max

/**
 * Service for working with resources.
 * Provides methods to find resources based on character skills and levels.
 */
@Service
class ItemService(
    private val itemRepository: ItemRepository,
    private val monsterService: MonsterService
) {
    private val logger = LoggerFactory.getLogger(ItemService::class.java)

    fun getBestCraftableItemsBySkillAndSubtypeAndMaxLevel(skillType: String, subType:String, maxLevel: Int): ItemDetails?{
        logger.debug("Getting resources for skill: $skillType with max level: $maxLevel")
        var items = itemRepository.findByCraftSkillAndSubtypeAndLevelLessThanEqualOrderByLevelDesc(skillType, subType, maxLevel)
        items = items.filter { it.craft != null } .toList()
        return ItemDocument.toItemDetails(items.first())
    }
    fun getAllCraftableItemsBySkillAndSubtypeAndMaxLevel(skillType: String, subType:String, maxLevel: Int): List<ItemDetails>{
        logger.debug("Getting resources for skill: $skillType with max level: $maxLevel")
        var items = itemRepository.findByCraftSkillAndSubtypeAndLevelLessThanEqualOrderByLevelDesc(skillType, subType, maxLevel)
        items = items.filter { it.craft != null }
        return items.map{ItemDocument.toItemDetails(it)}
    }

    fun getInvSizeToCraft(item: ItemDetails) : Int{
        var count = 0
        item.craft?.let { craft ->
            for(item in craft.items){
                count += item.quantity
                if(itemRepository.getByCode(item.code).craft != null){
                    count += getInvSizeToCraft(ItemDocument.toItemDetails(itemRepository.getByCode(item.code)))
                }
            }
        }
        return count
    }

    fun getWeightToCraft(item: ItemDetails) : Int{
        var count = 0
        item.craft?.let { craft ->
            for(item in craft.items){

                val subItem = itemRepository.getByCode(item.code)
                if(subItem.craft != null){
                    count += item.quantity * getInvSizeToCraft(ItemDocument.toItemDetails(subItem))
                }else if(subItem.subtype == "mob"){
                    count += item.quantity * (monsterService.findMonsterThatDrop(item.code)?.drops?.first { it.code == item.code }?.rate ?: 10)
                }else{
                    count += item.quantity
                }
            }
        }

        return count
    }

    fun getAllCraftBySkillUnderLevel(skill : String, level: Int ) : List<ItemDetails> {
        return itemRepository.findByCraftSkillAndLevelLessThanEqualOrderByLevelAsc(skill, level).map { ItemDocument.toItemDetails(it) }
    }

    fun getItem(itemCode: String) : ItemDetails {
        return ItemDocument.toItemDetails(itemRepository.getByCode(itemCode))
    }

    fun getCrafterItemsBetweenLevel(level: Int, minLevel: Int, skills : List<String>) : List<ItemDetails> {

        return itemRepository
            .findByLevelBetween(level, max(1,minLevel))
            .filter { it.craft != null && skills.contains(it.craft.skill) }
            .map { ItemDocument.toItemDetails(it) }
    }

    fun getHealingItems(inventory: List<SimpleItem>): List<SimpleItem> {
        val healingItems = itemRepository.findByEffectsCode("heal").map { it.code }
        return inventory.filter { healingItems.contains(it.code) }
    }

}
