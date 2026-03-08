package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.models.ItemDetails
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ItemRepository : MongoRepository<ItemDetails, String> {
    // Find by name containing (case-insensitive)
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<ItemDetails>

    // Find by type
    fun findByType(type: String, pageable: Pageable): Page<ItemDetails>

    // Find by code
    fun findByCode(code: String): ItemDetails

    // Find by codes
    fun findByCodeIn(codes: List<String>): List<ItemDetails>

    // Find by subtype
    fun findBySubtype(subtype: String, pageable: Pageable): Page<ItemDetails>

    // Find by level
    fun findByLevel(level: Int, pageable: Pageable): Page<ItemDetails>

    // Find by tradeable
    fun findByTradeable(tradable: Boolean, pageable: Pageable): Page<ItemDetails>

    fun getByCode(code: String) : ItemDetails

    fun findByCraftSkillAndSubtypeAndLevelLessThanEqualOrderByLevelDesc(skillType: String, subtype: String, maxLevel: Int) : List<ItemDetails>

    fun findByCraftSkillAndLevelLessThanEqualOrderByLevelDesc(skillType: String, maxLevel: Int) : List<ItemDetails>

    fun findByCraftSkillAndLevelLessThanEqualOrderByLevelAsc(skillType: String, maxLevel: Int) : List<ItemDetails>

    fun findByLevelBetween(minLevel: Int, maxLevel: Int) : List<ItemDetails>

    fun findByEffectsCode(code: String) : List<ItemDetails>

    fun findByCraftItemsCodeAndCraftSkillAndLevelIsLessThanEqual(code: String, skill: String, level: Int) : List<ItemDetails>
}
