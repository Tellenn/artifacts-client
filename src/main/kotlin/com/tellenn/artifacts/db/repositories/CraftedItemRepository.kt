package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.CraftedItemDocument
import com.tellenn.artifacts.db.documents.ItemDocument
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * Custom interface for CraftedItemRepository to add custom methods
 */
interface CraftedItemRepositoryCustom {
    fun save(item: CraftedItemDocument): CraftedItemDocument
}

@Repository
interface CraftedItemRepository : MongoRepository<CraftedItemDocument, String>, CraftedItemRepositoryCustom {

    fun findAllByQuantityLessThan(quantity: Int): List<CraftedItemDocument>


}
