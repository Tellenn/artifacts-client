package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.ItemDocument
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ItemRepository : MongoRepository<ItemDocument, String> {
    // Find by name containing (case-insensitive)
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<ItemDocument>

    // Find by type
    fun findByType(type: String, pageable: Pageable): Page<ItemDocument>

    // Find by code
    fun findByCode(code: String, pageable: Pageable): Page<ItemDocument>

    // Find by subtype
    fun findBySubtype(subtype: String, pageable: Pageable): Page<ItemDocument>

    // Find by level
    fun findByLevel(level: Int, pageable: Pageable): Page<ItemDocument>

    // Find by tradeable
    fun findByTradeable(tradable: Boolean, pageable: Pageable): Page<ItemDocument>

    // Custom query to find items with multiple criteria
    @Query("{}")
    fun findByDynamicQuery(pageable: Pageable): Page<ItemDocument>
}
