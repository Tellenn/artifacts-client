package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.MapClient
import com.tellenn.artifacts.db.repositories.MapRepository
import com.tellenn.artifacts.models.MapData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.Thread.sleep

@Service
class MapSyncService(
    private val mapClient: MapClient,
    private val mapRepository: MapRepository,
    private val serverVersionService: ServerVersionService
) {
    private val logger = LoggerFactory.getLogger(MapSyncService::class.java)

    /**
     * Empties the maps collection in MongoDB and fills it with map data from the API.
     * Syncs the entire map using predefined boundaries.
     *
     * @param pageSize The number of map chunks to fetch per page (default: 50)
     * @param forceSync Whether to force the sync regardless of server version (default: false)
     * @return The number of map chunks synced, or 0 if sync was not needed
     */
    @Transactional
    fun syncWholeMap(pageSize: Int = 50, forceSync: Boolean = false): Int {
        logger.info("Starting map sync process for the entire map")

        // Empty the database
        logger.debug("Emptying maps collection")
        mapRepository.deleteAll()

        var currentPage = 1
        var totalPages = 1
        var totalChunksProcessed = 0

        // Fetch all pages of map chunks
        do {
            logger.debug("Fetching maps page $currentPage of $totalPages")

            try {
                val response = mapClient.getMaps(
                    content_type = null,
                    content_code = null,
                    page = currentPage,
                    size = pageSize
                )
                var dataPage = response.data
                totalPages = response.pages

                dataPage = enrichMaps(dataPage)

                mapRepository.saveAll(dataPage)

                totalChunksProcessed += dataPage.size
                logger.debug("Processed ${dataPage.size} map chunks from page $currentPage")
                sleep(500)
                currentPage++
            } catch (e: Exception) {
                logger.error("Failed to fetch maps page $currentPage", e)
                break
            }
        } while (currentPage <= totalPages)

        // Save the server version after successful sync
        serverVersionService.updateServerVersion()
        logger.debug("Map sync completed and server version updated. Total chunks synced: $totalChunksProcessed")
        return totalChunksProcessed
    }

    /**
     * Syncs a single map chunk
     *
     * @param x The X coordinate
     * @param y The Y coordinate
     * @param name The name of the map (default: "map_x_y")
     * @param skin The skin of the map (default: "default")
     * @param forceSync Whether to force the sync regardless of server version (default: false)
     * @return True if the sync was successful, false if it failed or wasn't needed
     */
    @Transactional
    fun syncMapChunk(x: Int, y: Int, name: String = "map_${x}_${y}", skin: String = "default", forceSync: Boolean = false): Boolean {
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

            if (dataPage.isEmpty()) {
                logger.error("No map chunk found at ($x,$y)")
                return false
            }

            // Get the first (and only) map chunk
            val mapData = dataPage.first()

            // Convert MapData to MapDocument and save to MongoDB
            mapRepository.save(mapData)

            // Save the server version after successful sync
            serverVersionService.updateServerVersion()
            logger.info("Successfully synced map chunk at ($x,$y) and updated server version")
            return true
        } catch (e: Exception) {
            logger.error("Failed to sync map chunk at ($x,$y)", e)
            return false
        }
    }

    private fun enrichMaps(maps : List<MapData>) : List<MapData>{
        maps.forEach {
            if (it.skin.startsWith("mine_")) {
                it.region = 2
            }else if (it.skin.startsWith("mine3_8")) {
                it.region = 4
            }else if (it.skin.startsWith("mine3_")) {
                it.region = 3
            }else if (it.skin.startsWith("lichmine_")) {
                it.region = 4
            }else if (it.skin.startsWith("desertmine_6")) {
                it.region = 7
            }else if (it.skin.startsWith("desertmine_")) {
                it.region = 6
            }else if (it.skin.startsWith("desertisland_house")) {
                it.region = 10
            }else if (it.skin.startsWith("rosenblood_")) {
                it.region = 8
            }else if (it.skin.startsWith("abandonedhouse_")) {
                it.region = 9
            }else if (it.skin.startsWith("desertisland_")) {
                it.region = 5
            }else {
                it.region = 1
            }
            if(it.access?.type == "blocked"){
                it.region = 0
            }
        }
        return maps
    }
}
