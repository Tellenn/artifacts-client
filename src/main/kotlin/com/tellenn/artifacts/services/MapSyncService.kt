package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.MapClient
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
     * Since the map API doesn't support pagination, we fetch maps for a predefined area.
     *
     * @param startX The starting X coordinate
     * @param startY The starting Y coordinate
     * @param width The width of the area to sync (used for iteration only)
     * @param height The height of the area to sync (used for iteration only)
     * @param chunkSize The size of each chunk to fetch (default: 10, used for iteration only)
     * @return The number of map chunks synced
     */
    @Transactional
    fun syncMapArea(startX: Int, startY: Int, width: Int, height: Int, chunkSize: Int = 10): Int {
        logger.info("Starting map sync process for area: ($startX,$startY) to (${startX + width - 1},${startY + height - 1})")
        println("[DEBUG_LOG] Starting map sync process for area: ($startX,$startY) to (${startX + width - 1},${startY + height - 1})")

        // Empty the database
        logger.info("Emptying maps collection")
        println("[DEBUG_LOG] Emptying maps collection")
        mapRepository.deleteAll()

        var totalChunksProcessed = 0

        // Fetch map data in chunks
        for (x in startX until startX + width step chunkSize) {
            for (y in startY until startY + height step chunkSize) {
                // Calculate actual chunk dimensions (handle edge cases)
                val chunkWidth = minOf(chunkSize, startX + width - x)
                val chunkHeight = minOf(chunkSize, startY + height - y)

                logger.info("Fetching map chunk at ($x,$y) with dimensions ${chunkWidth}x${chunkHeight}")
                println("[DEBUG_LOG] Fetching map chunk at ($x,$y) with dimensions ${chunkWidth}x${chunkHeight}")
                try {
                    val mapName = "map_${x}_${y}"
                    val response = mapClient.getMap(x = x, y = y, name = mapName, skin = "default")
                    val mapData = response.data

                    // Convert MapData to MapDocument and save to MongoDB
                    val mapDocument = MapDocument.fromMapData(mapData)
                    mapRepository.save(mapDocument)

                    totalChunksProcessed++
                    logger.info("Processed map chunk at ($x,$y)")
                    println("[DEBUG_LOG] Processed map chunk at ($x,$y)")
                } catch (e: Exception) {
                    logger.error("Failed to fetch map chunk at ($x,$y)", e)
                    println("[DEBUG_LOG] Failed to fetch map chunk at ($x,$y): ${e.message}")
                    // Continue to the next chunk
                    continue
                }
            }
        }

        logger.info("Map sync completed. Total chunks synced: $totalChunksProcessed")
        return totalChunksProcessed
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
            val response = mapClient.getMap(x = x, y = y, name = name, skin = skin)
            val mapData = response.data

            // Convert MapData to MapDocument and save to MongoDB
            val mapDocument = MapDocument.fromMapData(mapData)
            mapRepository.save(mapDocument)

            logger.info("Successfully synced map chunk at ($x,$y)")
            return true
        } catch (e: Exception) {
            logger.error("Failed to sync map chunk at ($x,$y)", e)
            return false
        }
    }
}
