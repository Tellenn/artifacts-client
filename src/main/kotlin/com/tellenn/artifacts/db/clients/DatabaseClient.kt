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
        subtype: String? = null,
        level: Int? = null,
        tradeable: Boolean? = null,
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
            subtype != null -> itemRepository.findBySubtype(subtype, pageable)
            level != null -> itemRepository.findByLevel(level, pageable)
            tradeable != null -> itemRepository.findByTradeable(tradeable, pageable)
            else -> itemRepository.findAll(pageable)
        }

        return ArtifactsArrayResponseBody(result.map({ it -> convertToItemDetails(it) }).toList(),
            result.totalElements.toInt(), page, result.size, result.totalPages)
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
     * Convert ItemDocument to ItemDetails using the mapper in ItemDocument.
     */
    private fun convertToItemDetails(itemDocument: ItemDocument): ItemDetails {
        return ItemDocument.toItemDetails(itemDocument)
    }
}
