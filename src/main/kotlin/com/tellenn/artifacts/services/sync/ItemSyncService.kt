package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.ItemClient
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.utils.PaginatedSyncUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ItemSyncService(
    private val itemClient: ItemClient,
    private val itemRepository: ItemRepository,
) {
    private val logger = LoggerFactory.getLogger(ItemSyncService::class.java)

    @Transactional
    fun syncAllItems(forceSync: Boolean = false): Int =
        PaginatedSyncUtils.syncAll(
            logger = logger,
            label = "items",
            clearFn = itemRepository::deleteAll,
            fetchPage = { page, size -> itemClient.getItems(page = page, size = size) },
            persistFn = itemRepository::saveAll,
        )
}
