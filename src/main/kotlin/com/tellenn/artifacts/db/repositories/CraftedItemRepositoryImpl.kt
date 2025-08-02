package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.CraftedItemDocument
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

/**
 * Implementation of CraftedItemRepositoryCustom interface.
 * This class provides custom save logic for CraftedItemDocument.
 */
@Component
class CraftedItemRepositoryImpl : CraftedItemRepositoryCustom {

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    /**
     * Custom save method that adds to the quantity if an item with the same code already exists.
     * 
     * @param item The CraftedItemDocument to save
     * @return The saved CraftedItemDocument
     */
    override fun save(item: CraftedItemDocument): CraftedItemDocument {
        // Check if an item with the same code already exists
        val query = Query.query(Criteria.where("code").`is`(item.code))
        val existingItem = mongoTemplate.findOne(query, CraftedItemDocument::class.java)
        
        if (existingItem != null) {
            // If it exists, add to the quantity
            val updatedQuantity = existingItem.quantity + item.quantity
            
            // Create a new document with the updated quantity
            val updatedItem = CraftedItemDocument(
                code = existingItem.code,
                name = existingItem.name,
                description = existingItem.description,
                type = existingItem.type,
                subtype = existingItem.subtype,
                level = existingItem.level,
                tradeable = existingItem.tradeable,
                effects = existingItem.effects,
                craft = existingItem.craft,
                conditions = existingItem.conditions,
                quantity = updatedQuantity
            )
            
            // Save the updated document
            return mongoTemplate.save(updatedItem)
        } else {
            // If it doesn't exist, perform a normal save
            return mongoTemplate.save(item)
        }
    }
}