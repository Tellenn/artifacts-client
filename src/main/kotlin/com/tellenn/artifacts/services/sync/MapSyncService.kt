package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.MapClient
import com.tellenn.artifacts.db.repositories.MapRepository
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.utils.PaginatedSyncUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MapSyncService(
    private val mapClient: MapClient,
    private val mapRepository: MapRepository,
) {
    private val logger = LoggerFactory.getLogger(MapSyncService::class.java)

    @Transactional
    fun syncWholeMap(pageSize: Int = PaginatedSyncUtils.DEFAULT_PAGE_SIZE, forceSync: Boolean = false): Int {
        logger.info("Starting map sync")
        mapRepository.deleteAll()

        val allMaps = PaginatedSyncUtils.collectAll(
            logger = logger,
            label = "maps",
            pageSize = pageSize,
            fetchPage = { page, size -> mapClient.getMaps(content_type = null, content_code = null, page = page, size = size) },
        )

        logger.info("Enriching ${allMaps.size} maps")
        val enrichedMaps = autoEnrichMaps(allMaps)
        logger.info("Saving ${enrichedMaps.size} enriched maps")
        mapRepository.saveAll(enrichedMaps)
        return enrichedMaps.size
    }

    /***
     * A way to create regions automatically.
     * Two adjacent maps (x +/- 1 or y +/- 1, same layer) belong to the same region if and only if
     * they share the same access type (null, "blocked", "restricted", …).
     * This means each access category forms its own set of isolated connected components.
     *
     * Special rule : spawn (0, 0, "overworld") is always region 1.
     */
    private fun autoEnrichMaps(maps: List<MapData>): List<MapData> {
        val mapByCoords = maps.associateBy { Triple(it.x, it.y, it.layer) }
        val visited = mutableSetOf<Triple<Int, Int, String>>()
        var nextRegionId = 2

        // Handle spawn: (0, 0) on "overworld" is always region 1
        val spawnCoords = Triple(0, 0, "overworld")
        mapByCoords[spawnCoords]?.let { spawn ->
            assignRegion(spawn, 1, mapByCoords, visited)
        }

        // Assign a unique region to every remaining unvisited connected component
        maps.forEach { mapData ->
            val coords = Triple(mapData.x, mapData.y, mapData.layer)
            if (!visited.contains(coords)) {
                assignRegion(mapData, nextRegionId++, mapByCoords, visited)
            }
        }

        return maps
    }

    private fun assignRegion(
        startMap: MapData,
        regionId: Int,
        mapByCoords: Map<Triple<Int, Int, String>, MapData>,
        visited: MutableSet<Triple<Int, Int, String>>
    ) {
        val queue = ArrayDeque<MapData>()
        queue.add(startMap)
        visited.add(Triple(startMap.x, startMap.y, startMap.layer))
        startMap.region = regionId

        val directions = listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (dir in directions) {
                val neighborCoords = Triple(current.x + dir.first, current.y + dir.second, current.layer)
                val neighbor = mapByCoords[neighborCoords] ?: continue
                if (visited.contains(neighborCoords)) continue
                // Only propagate within the same access category so that blocked,
                // restricted, and open areas each form their own isolated regions.
                if (neighbor.access?.type == current.access?.type) {
                    neighbor.region = regionId
                    visited.add(neighborCoords)
                    queue.add(neighbor)
                }
            }
        }
    }
}
