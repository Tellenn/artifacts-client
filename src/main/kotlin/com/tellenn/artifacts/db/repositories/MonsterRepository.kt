package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.MonsterDocument
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MonsterRepository : MongoRepository<MonsterDocument, String> {
    // Find by name
    fun findByName(name: String, pageable: Pageable): Page<MonsterDocument>
    
    // Find by level
    fun findByLevel(level: Int, pageable: Pageable): Page<MonsterDocument>
    
    // Find by level range
    fun findByLevelBetween(minLevel: Int, maxLevel: Int, pageable: Pageable): Page<MonsterDocument>
    
    // Find monsters that drop a specific item
    @Query("{ 'drops.itemId': ?0 }")
    fun findByDropItemId(itemId: String, pageable: Pageable): Page<MonsterDocument>
    
    // Custom query to find monsters with multiple criteria
    @Query("{}")
    fun findByDynamicQuery(pageable: Pageable): Page<MonsterDocument>
}