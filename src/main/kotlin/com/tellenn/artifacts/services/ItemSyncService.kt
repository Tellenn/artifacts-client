package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.ItemClient
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ItemSyncService(
    private val itemClient: ItemClient,
    private val itemRepository: ItemRepository
) {
    private val logger = LoggerFactory.getLogger(ItemSyncService::class.java)

    /**
     * Empties the items collection in MongoDB and fills it with all items from the API.
     * Handles pagination to fetch all items.
     *
     * @return The number of items synced
     */
    @Transactional
    fun syncAllItems(): Int {
        logger.info("Starting item sync process")

        // Empty the database
        logger.info("Emptying items collection")
        itemRepository.deleteAll()

        var currentPage = 1
        var totalPages = 1
        var totalItemsProcessed = 0
        val pageSize = 50 // Maximum page size supported by the API

        // Fetch all pages of items
        do {
            logger.info("Fetching items page $currentPage of $totalPages")
            try {
                val response = itemClient.getItems(page = currentPage, size = pageSize)
                val dataPage = response.data
                totalPages = dataPage.pages

                // Convert ItemDetails to ItemDocument and save to MongoDB
                val itemDocuments = dataPage.items.map { ItemDocument.fromItemDetails(it) }
                itemRepository.saveAll(itemDocuments)

                totalItemsProcessed += dataPage.items.size
                logger.info("Processed ${dataPage.items.size} items from page $currentPage")

                currentPage++
            } catch (e: Exception) {
                logger.error("Failed to fetch items", e)
                break
            }
        } while (currentPage <= totalPages)

        logger.info("Item sync completed. Total items synced: $totalItemsProcessed")
        return totalItemsProcessed
    }
}
