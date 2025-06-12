package com.tellenn.artifacts.db.clients

import com.tellenn.artifacts.clients.models.ItemDetails
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

/**
 * Client for interacting with the MongoDB database.
 * Provides methods similar to the API clients but fetches data from the local database.
 */
@Component
class DatabaseClient(private val itemRepository: ItemRepository) {

    /**
     * Get items from the database with filtering and pagination.
     * This method mimics the behavior of ItemClient.getItems but fetches from the database.
     */
    fun getItems(
        name: String? = null,
        type: String? = null,
        rarity: String? = null,
        level: Int? = null,
        equippable: Boolean? = null,
        usable: Boolean? = null,
        stackable: Boolean? = null,
        slot: String? = null,
        page: Int = 1,
        size: Int = 50
    ): ArtifactsArrayResponseBody<ItemDetails> {
        // Create pageable object for Spring Data
        val pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "name"))
        
        // Determine which repository method to use based on provided filters
        val result = when {
            // If multiple filters are provided, we would need more complex queries
            // For simplicity, we're prioritizing filters in a specific order
            name != null -> itemRepository.findByNameContainingIgnoreCase(name, pageable)
            type != null -> itemRepository.findByType(type, pageable)
            rarity != null -> itemRepository.findByRarity(rarity, pageable)
            level != null -> itemRepository.findByLevel(level, pageable)
            equippable != null -> itemRepository.findByEquippable(equippable, pageable)
            usable != null -> itemRepository.findByUsable(usable, pageable)
            stackable != null -> itemRepository.findByStackable(stackable, pageable)
            slot != null -> itemRepository.findBySlot(slot, pageable)
            else -> itemRepository.findAll(pageable)
        }
        
        return ArtifactsArrayResponseBody(result.map({ it -> convertToItemDetails(it) }).toList(),
            result.totalElements.toInt(),result.totalPages, result.size, result.totalPages)
    }
    
    /**
     * Get item details by item code.
     * This method mimics the behavior of ItemClient.getItemDetails but fetches from the database.
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
            rarity = itemDocument.rarity,
            level = itemDocument.level,
            value = itemDocument.value,
            weight = itemDocument.weight,
            stackable = itemDocument.stackable,
            usable = itemDocument.usable,
            equippable = itemDocument.equippable,
            slot = itemDocument.slot,
            stats = itemDocument.stats?.let { convertToItemStats(it) },
            recipe = itemDocument.recipe?.let { convertToItemRecipe(it) }
        )
    }
    
    /**
     * Convert ItemStatsDocument to ItemStats.
     */
    private fun convertToItemStats(statsDocument: com.tellenn.artifacts.db.documents.ItemStatsDocument): com.tellenn.artifacts.clients.models.ItemStats {
        return com.tellenn.artifacts.clients.models.ItemStats(
            hp = statsDocument.hp,
            attackFire = statsDocument.attackFire,
            attackEarth = statsDocument.attackEarth,
            attackWater = statsDocument.attackWater,
            attackAir = statsDocument.attackAir,
            dmgFire = statsDocument.dmgFire,
            dmgEarth = statsDocument.dmgEarth,
            dmgWater = statsDocument.dmgWater,
            dmgAir = statsDocument.dmgAir,
            resFire = statsDocument.resFire,
            resEarth = statsDocument.resEarth,
            resWater = statsDocument.resWater,
            resAir = statsDocument.resAir
        )
    }
    
    /**
     * Convert ItemRecipeDocument to ItemRecipe.
     */
    private fun convertToItemRecipe(recipeDocument: com.tellenn.artifacts.db.documents.ItemRecipeDocument): com.tellenn.artifacts.clients.models.ItemRecipe {
        return com.tellenn.artifacts.clients.models.ItemRecipe(
            skill = recipeDocument.skill,
            level = recipeDocument.level,
            ingredients = recipeDocument.ingredients.map { convertToRecipeIngredient(it) }
        )
    }
    
    /**
     * Convert RecipeIngredientDocument to RecipeIngredient.
     */
    private fun convertToRecipeIngredient(ingredientDocument: com.tellenn.artifacts.db.documents.RecipeIngredientDocument): com.tellenn.artifacts.clients.models.RecipeIngredient {
        return com.tellenn.artifacts.clients.models.RecipeIngredient(
            code = ingredientDocument.code,
            name = ingredientDocument.name,
            quantity = ingredientDocument.quantity
        )
    }
}