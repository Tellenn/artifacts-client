package com.tellenn.artifacts.runners

import com.tellenn.artifacts.services.ItemSyncService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("item-sync")
class ItemSyncRunner(private val itemSyncService: ItemSyncService) : CommandLineRunner {
    
    private val logger = LoggerFactory.getLogger(ItemSyncRunner::class.java)
    
    override fun run(vararg args: String) {
        logger.info("Starting item sync runner")
        val itemCount = itemSyncService.syncAllItems()
        logger.info("Item sync completed. Synced $itemCount items")
    }
}