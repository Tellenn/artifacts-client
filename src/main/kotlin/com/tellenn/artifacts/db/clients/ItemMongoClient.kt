package com.tellenn.artifacts.db.clients

import com.tellenn.artifacts.clients.models.ItemDetails
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

/**
 * Client for interacting with the MongoDB database specifically for Item operations.
 * Provides methods to fetch and filter items from the local database.
 */
@Component
class ItemMongoClient(
    private val itemRepository: ItemRepository,
    private val mongoTemplate: MongoTemplate
) {

    /**
     * Get items from the database with filtering and pagination.
     * Supports filtering by name, type, subtype, level, and tradeable status.
     */
    fun getItems(
        name: String? = null,
        type: String? = null,
        subtype: String? = null,
        level: Int? = null,
        minLevel: Int? = null,
        maxLevel: Int? = null,
        tradeable: Boolean? = null,
        page: Int = 1,
        size: Int = 50
    ): ArtifactsArrayResponseBody<ItemDetails> {
        // Create pageable object for Spring Data
        val pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "name"))

        // If we have complex filtering with level ranges, use MongoTemplate with Query
        if (minLevel != null || maxLevel != null) {
            val query = Query()
            
            // Add criteria for each filter
            name?.let { query.addCriteria(Criteria.where("name").regex(it, "i")) }
            type?.let { query.addCriteria(Criteria.where("type").`is`(it)) }
            subtype?.let { query.addCriteria(Criteria.where("subtype").`is`(it)) }
            level?.let { query.addCriteria(Criteria.where("level").`is`(it)) }
            tradeable?.let { query.addCriteria(Criteria.where("tradeable").`is`(it)) }
            
            // Add level range criteria
            if (minLevel != null || maxLevel != null) {
                val levelCriteria = Criteria.where("level")
                minLevel?.let { levelCriteria.gte(it) }
                maxLevel?.let { levelCriteria.lte(it) }
                query.addCriteria(levelCriteria)
            }
            
            // Apply pagination
            query.with(pageable)
            
            // Execute query
            val items = mongoTemplate.find(query, ItemDocument::class.java)
            val count = mongoTemplate.count(query.skip(-1).limit(-1), ItemDocument::class.java)
            
            return ArtifactsArrayResponseBody(
                items.map { convertToItemDetails(it) },
                count.toInt(),
                page,
                size,
                (count / size).toInt() + if (count % size > 0) 1 else 0
            )
        } else {
            // For simple filtering, use repository methods
            val result = when {
                name != null -> itemRepository.findByNameContainingIgnoreCase(name, pageable)
                type != null -> itemRepository.findByType(type, pageable)
                subtype != null -> itemRepository.findBySubtype(subtype, pageable)
                level != null -> itemRepository.findByLevel(level, pageable)
                tradeable != null -> itemRepository.findByTradeable(tradeable, pageable)
                else -> itemRepository.findAll(pageable)
            }

            return ArtifactsArrayResponseBody(
                result.map { convertToItemDetails(it) }.toList(),
                result.totalElements.toInt(),
                page,
                result.size,
                result.totalPages
            )
        }
    }

    /**
     * Get item details by item code.
     */
    fun getItemDetails(itemCode: String): ArtifactsResponseBody<ItemDetails> {
        val itemDocument = itemRepository.findById(itemCode)
            .orElseThrow { NoSuchElementException("Item with code $itemCode not found") }

        return ArtifactsResponseBody(convertToItemDetails(itemDocument))
    }

    /**
     * Convert ItemDocument to ItemDetails.
     */
    private fun convertToItemDetails(itemDocument: ItemDocument): ItemDetails {
        return ItemDetails(
            code = itemDocument.code,
            name = itemDocument.name,
            description = itemDocument.description,
            type = itemDocument.type,
            subtype = itemDocument.subtype,
            level = itemDocument.level,
            tradeable = itemDocument.tradeable,
            effects = itemDocument.effects?.map { convertToItemEffect(it) }?.toList(),
            craft = itemDocument.craft?.let { convertToItemCraft(it) }
        )
    }

    /**
     * Convert ItemEffectDocument to Effect.
     */
    private fun convertToItemEffect(effectDocument: com.tellenn.artifacts.db.documents.ItemEffectDocument): com.tellenn.artifacts.clients.models.Effect {
        return com.tellenn.artifacts.clients.models.Effect(
            code = effectDocument.code,
            value = effectDocument.value
        )
    }

    /**
     * Convert ItemCraftDocument to ItemCraft.
     */
    private fun convertToItemCraft(craftDocument: com.tellenn.artifacts.db.documents.ItemCraftDocument): com.tellenn.artifacts.clients.models.ItemCraft {
        return com.tellenn.artifacts.clients.models.ItemCraft(
            skill = craftDocument.skill,
            level = craftDocument.level,
            items = craftDocument.items.map { convertToRecipeIngredient(it) },
            quantity = craftDocument.quantity
        )
    }

    /**
     * Convert RecipeIngredientDocument to RecipeIngredient.
     */
    private fun convertToRecipeIngredient(ingredientDocument: com.tellenn.artifacts.db.documents.RecipeIngredientDocument): com.tellenn.artifacts.clients.models.RecipeIngredient {
        return com.tellenn.artifacts.clients.models.RecipeIngredient(
            code = ingredientDocument.code,
            quantity = ingredientDocument.quantity
        )
    }
}