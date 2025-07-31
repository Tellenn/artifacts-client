package com.tellenn.artifacts.services

import com.tellenn.artifacts.db.clients.MapMongoClient
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.db.repositories.ResourceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for working with resources.
 * Provides methods to find resources based on character skills and levels.
 */
@Service
class ItemService(
    private val mapProximityService: MapProximityService,
    private val mapMongoClient: MapMongoClient,
    private val resourceRepository: ResourceRepository,
    private val itemRepository: ItemRepository
) {
    private val logger = LoggerFactory.getLogger(ItemService::class.java)

    fun getBestCraftableItemsBySkillAndSubtypeAndMaxLevel(skillType: String, subType:String, maxLevel: Int): ItemDocument?{
        logger.info("Getting resources for skill: $skillType with max level: $maxLevel")
        var items = itemRepository.findByCraftSkillAndSubtypeAndLevelLessThanEqualOrderByLevelDesc(skillType, subType, maxLevel)
        items = items.filter { it.craft != null } .toList()
        return items.first()
    }

    fun getInvSizeToCraft(item: ItemDocument) : Int{
        var count = 0
        item.craft?.let { craft ->
            for(item in craft.items){
                count += item.quantity
                if(itemRepository.getByCode(item.code).craft != null){
                    count += getInvSizeToCraft(itemRepository.getByCode(item.code))
                }
            }
        }
        return count
    }

    fun getAllCraftBySkillUnderLevel(skill : String, level: Int ) : List<ItemDocument> {
        return itemRepository.findByCraftSkillAndLevelLessThanEqualOrderByLevelAsc(skill, level)
    }

}
