package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.ItemClient
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.Thread.sleep

@Service
class ItemSyncService(
    private val itemClient: ItemClient,
    private val itemRepository: ItemRepository,
    private val serverVersionService: ServerVersionService
) {
    private val logger = LoggerFactory.getLogger(ItemSyncService::class.java)

    /**
     * Empties the items collection in MongoDB and fills it with all items from the API.
     * Handles pagination to fetch all items.
     *
     * @param forceSync Whether to force the sync regardless of server version (default: false)
     * @return The number of items synced
     */
    @Transactional
    fun syncAllItems(forceSync: Boolean = false): Int {
        logger.debug("Starting item sync process")

        // Empty the database
        logger.debug("Emptying items collection")
        itemRepository.deleteAll()

        var currentPage = 1
        var totalPages = 1
        var totalItemsProcessed = 0
        val pageSize = 50 // Maximum page size supported by the API

        // Fetch all pages of items
        do {
            logger.debug("Fetching items page $currentPage of $totalPages")
            try {
                val response = itemClient.getItems(page = currentPage, size = pageSize)
                totalPages = response.pages

                // Convert ItemDetails to ItemDocument and save to MongoDB
                val itemDocuments = response.data.map { ItemDocument.fromItemDetails(it) }
                itemRepository.saveAll(itemDocuments)

                totalItemsProcessed += response.data.size
                logger.debug("Processed ${response.data.size} items from page $currentPage")
                sleep(500)
                currentPage++
            } catch (e: Exception) {
                logger.error("Failed to fetch items", e)
                break
            }
        } while (currentPage <= totalPages)

        // Save the server version after successful sync
        serverVersionService.updateServerVersion()
        logger.info("Item sync completed and server version updated. Total items synced: $totalItemsProcessed")
        return totalItemsProcessed
    }
}