package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.MapClient
import com.tellenn.artifacts.clients.models.MapData
import com.tellenn.artifacts.db.documents.MapDocument
import com.tellenn.artifacts.db.repositories.MapRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MapSyncService(
    private val mapClient: MapClient,
    private val mapRepository: MapRepository
) {
    private val logger = LoggerFactory.getLogger(MapSyncService::class.java)

    /**
     * Empties the maps collection in MongoDB and fills it with map data from the API.
     * Syncs the entire map using predefined boundaries.
     *
     * @param pageSize The number of map chunks to fetch per page (default: 50)
     * @return The number of map chunks synced
     */
    @Transactional
    fun syncWholeMap(pageSize: Int = 50): Int {
        logger.info("Starting map sync process for the entire map")
        println("[DEBUG_LOG] Starting map sync process for the entire map")

        // Empty the database
        logger.info("Emptying maps collection")
        println("[DEBUG_LOG] Emptying maps collection")
        mapRepository.deleteAll()

        // Define the starting point for the map
        // Using the same values as in the tests (0,0)
        val startX = 0
        val startY = 0

        var currentPage = 1
        var totalPages = 1
        var totalChunksProcessed = 0

        // Fetch all pages of map chunks
        do {
            logger.info("Fetching maps page $currentPage of $totalPages")
            println("[DEBUG_LOG] Fetching maps page $currentPage of $totalPages")

            try {
                val response = mapClient.getMaps(
                    content_type = null,
                    content_code = null,
                    page = currentPage,
                    size = pageSize
                )
                val dataPage = response.data
                totalPages = dataPage.pages

                // Convert MapData to MapDocument and save to MongoDB in batch
                val mapDocuments = dataPage.items.map { MapDocument.fromMapData(it) }
                mapRepository.saveAll(mapDocuments)

                totalChunksProcessed += dataPage.items.size
                logger.info("Processed ${dataPage.items.size} map chunks from page $currentPage")
                println("[DEBUG_LOG] Processed ${dataPage.items.size} map chunks from page $currentPage")

                currentPage++
            } catch (e: Exception) {
                logger.error("Failed to fetch maps page $currentPage", e)
                println("[DEBUG_LOG] Failed to fetch maps page $currentPage: ${e.message}")
                break
            }
        } while (currentPage <= totalPages)

        logger.info("Map sync completed. Total chunks synced: $totalChunksProcessed")
        return totalChunksProcessed
    }

    /**
     * @deprecated Use syncWholeMap() instead. This method will be removed in a future release.
     */
    @Transactional
    @Deprecated("Use syncWholeMap() instead", ReplaceWith("syncWholeMap(chunkSize)"))
    fun syncMapArea(startX: Int, startY: Int, width: Int, height: Int, chunkSize: Int = 10): Int {
        logger.info("syncMapArea is deprecated. Using syncWholeMap instead.")
        return syncWholeMap(chunkSize)
    }

    /**
     * Syncs a single map chunk
     *
     * @param x The X coordinate
     * @param y The Y coordinate
     * @param name The name of the map (default: "map_x_y")
     * @param skin The skin of the map (default: "default")
     * @return True if the sync was successful, false otherwise
     */
    @Transactional
    fun syncMapChunk(x: Int, y: Int, name: String = "map_${x}_${y}", skin: String = "default"): Boolean {
        logger.info("Syncing map chunk at ($x,$y)")
        try {
            // Use the batch API to get just one chunk
            val response = mapClient.getMaps(
                name = name,
                content_type = null,
                content_code = null,
                page = 1,
                size = 1
            )
            val dataPage = response.data

            if (dataPage.items.isEmpty()) {
                logger.error("No map chunk found at ($x,$y)")
                return false
            }

            // Get the first (and only) map chunk
            val mapData = dataPage.items.first()

            // Override the name if it's different from the default
            val mapDataWithCorrectName = if (mapData.name != name) {
                MapData(
                    name = name,
                    skin = mapData.skin,
                    x = mapData.x,
                    y = mapData.y,
                    content = mapData.content
                )
            } else {
                mapData
            }

            // Convert MapData to MapDocument and save to MongoDB
            val mapDocument = MapDocument.fromMapData(mapDataWithCorrectName)
            mapRepository.save(mapDocument)

            logger.info("Successfully synced map chunk at ($x,$y)")
            return true
        } catch (e: Exception) {
            logger.error("Failed to sync map chunk at ($x,$y)", e)
            return false
        }
    }
}
