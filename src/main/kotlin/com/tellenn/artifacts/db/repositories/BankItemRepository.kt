package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.BankItemDocument
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface BankItemRepository : MongoRepository<BankItemDocument, String> {
    // Find by name containing (case-insensitive)
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<BankItemDocument>

    // Find by type
    fun findByType(type: String, pageable: Pageable): Page<BankItemDocument>

    // Find By Item Code
    fun findByCode(code: String): BankItemDocument?

    // Find by subtype
    fun findBySubtype(subtype: String, pageable: Pageable): Page<BankItemDocument>

    // Find by level
    fun findByLevel(level: Int, pageable: Pageable): Page<BankItemDocument>

    // Find by tradeable
    fun findByTradeable(tradable: Boolean, pageable: Pageable): Page<BankItemDocument>

    // Custom query to find items with multiple criteria
    @Query("{}")
    fun findByDynamicQuery(pageable: Pageable): Page<BankItemDocument>
}
