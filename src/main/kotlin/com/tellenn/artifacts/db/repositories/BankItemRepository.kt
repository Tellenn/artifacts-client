package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.BankItemDocument
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface BankItemRepository : MongoRepository<BankItemDocument, String> {
    // Find By Item Code
    fun findByCode(code: String): BankItemDocument?

    fun findByTypeInAndLevelIsLessThanEqual(type: List<String>, level: Int): List<BankItemDocument>

    fun findByCodeContainingIgnoreCase(code: String): List<BankItemDocument>
}
