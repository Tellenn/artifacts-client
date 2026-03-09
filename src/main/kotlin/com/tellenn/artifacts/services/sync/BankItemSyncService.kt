package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BankItemSyncService(
    private val itemRepository: ItemRepository,
    private val bankClient: BankClient,
    private val bankRepository: BankItemRepository
) {
    private val logger = LoggerFactory.getLogger(BankItemSyncService::class.java)

    /**
     * Empties the items collection in MongoDB and fills it with all items from the API.
     * Handles pagination to fetch all items.
     *
     * @return The number of items synced
     */
    @Transactional
    fun syncAllItems(): Int {
        logger.debug("Starting bank sync process")

        // Empty the database
        logger.debug("Emptying bank collection")
        bankRepository.deleteAll()

        var count = 0
        try {
            var response = bankClient.getBankedItems()
            val items = itemRepository.findAll()

            response.data.forEach { item ->
                val matchingItem = items.filter { i -> i.code == item.code }[0]
                bankRepository.insert<BankItemDocument>(BankItemDocument.fromItemDetails(matchingItem, item.quantity))
                count++
            }

            while(response.pages > response.page) {
                response = bankClient.getBankedItems(page = response.page + 1)
                response.data.forEach { item ->
                    val matchingItem = items.filter { i -> i.code == item.code }[0]
                    bankRepository.insert<BankItemDocument>(BankItemDocument.fromItemDetails(matchingItem, item.quantity))
                    count++
                }
            }

            logger.debug("Bank sync completed and server version updated")

        } catch (e: Exception) {
            logger.error("Failed to fetch bank items", e)
        }

        return count
    }
}