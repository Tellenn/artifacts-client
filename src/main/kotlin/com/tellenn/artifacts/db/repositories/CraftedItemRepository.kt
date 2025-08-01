package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.CraftedItemDocument
import com.tellenn.artifacts.db.documents.ItemDocument
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CraftedItemRepository : MongoRepository<CraftedItemDocument, String> {

    fun findByLevelBetween(minLevel: Int, maxLevel: Int) : List<CraftedItemDocument>

}
