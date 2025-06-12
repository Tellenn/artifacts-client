package com.tellenn.artifacts.examples

import com.tellenn.artifacts.db.clients.DatabaseClient
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Example showing how to use the DatabaseClient to query items from the database.
 * This class will run automatically when the application starts with the "db-client-example" profile.
 */
@Component
@Profile("db-client-example")
class DatabaseClientExample(private val databaseClient: DatabaseClient) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(DatabaseClientExample::class.java)

    override fun run(vararg args: String) {
        logger.info("Starting DatabaseClient example")

        // Example 1: Get all items (paginated)
        val allItems = databaseClient.getItems(page = 1, size = 10)
        logger.info("Found ${allItems.total} total items, showing page ${allItems.page} of ${allItems.pages}")
        allItems.data.forEach { item ->
            logger.info("Item: ${item.code} - ${item.name} (${item.subtype} ${item.type})")
        }

        // Example 2: Search items by name
        val searchResult = databaseClient.getItems(name = "sword", page = 1, size = 10)
        logger.info("Found ${searchResult.total} items matching 'sword'")
        searchResult.data.forEach { item ->
            logger.info("Item: ${item.code} - ${item.name} (${item.subtype} ${item.type})")
        }

        // Example 3: Filter items by type
        val weaponItems = databaseClient.getItems(type = "weapon", page = 1, size = 10)
        logger.info("Found ${weaponItems.total} weapon items")
        weaponItems.data.forEach { item ->
            logger.info("Weapon: ${item.code} - ${item.name} (${item.subtype})")
        }

        // Example 4: Get item details by code
        try {
            // Note: You need to replace "ITEM_CODE" with an actual item code that exists in your database
            val itemCode = if (allItems.data.isNotEmpty()) allItems.data[0].code else "ITEM_CODE"
            val itemDetails = databaseClient.getItemDetails(itemCode)
            val item = itemDetails.data
            logger.info("Item details for $itemCode:")
            logger.info("Name: ${item.name}")
            logger.info("Description: ${item.description ?: "N/A"}")
            logger.info("Type: ${item.type}")
            logger.info("Subtype: ${item.subtype}")
            logger.info("Level: ${item.level}")
            logger.info("Tradable: ${item.tradable}")
            logger.info("Slot: ${item.slot ?: "N/A"}")
            logger.info("Effect: ${if (item.effect != null) "${item.effect.code}: ${item.effect.value}" else "N/A"}")
            logger.info("Craft: ${if (item.craft != null) "${item.craft.skill} (level ${item.craft.level})" else "N/A"}")
        } catch (e: Exception) {
            logger.error("Error getting item details", e)
        }

        logger.info("DatabaseClient example completed")
    }
}
