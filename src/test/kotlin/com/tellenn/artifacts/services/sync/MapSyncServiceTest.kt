package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.MapClient
import com.tellenn.artifacts.db.repositories.MapRepository
import com.tellenn.artifacts.models.Access
import com.tellenn.artifacts.models.MapData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class MapSyncServiceTest {

    private lateinit var mapClient: MapClient
    private lateinit var mapRepository: MapRepository
    private lateinit var serverVersionService: ServerVersionService
    private lateinit var mapSyncService: MapSyncService

    @BeforeEach
    fun setUp() {
        mapClient = mock(MapClient::class.java)
        mapRepository = mock(MapRepository::class.java)
        serverVersionService = mock(ServerVersionService::class.java)
        mapSyncService = MapSyncService(mapClient, mapRepository)
    }

    @Test
    fun `autoEnrichMaps should correctly group adjacent maps into regions`() {
        // Create a 3x3 grid on overworld
        // (0,0) (1,0) (2,0)
        // (0,1) BLOCKED (2,1)
        // (0,2) (1,2) (2,2)
        
        val maps = mutableListOf<MapData>()
        for (x in 0..2) {
            for (y in 0..2) {
                val access = if (x == 1 && y == 1) Access("blocked", emptyList()) else null
                maps.add(createMap(x, y, "overworld", access))
            }
        }
        
        // Add another layer
        maps.add(createMap(0, 0, "cave"))
        maps.add(createMap(1, 0, "cave"))
        
        // Use reflection to call private method for testing
        val method = MapSyncService::class.java.getDeclaredMethod("autoEnrichMaps", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val enriched = method.invoke(mapSyncService, maps) as List<MapData>
        
        val enrichedMap = enriched.associateBy { Triple(it.x, it.y, it.layer) }
        
        // Spawn (0,0, overworld) should be region 1
        assertEquals(1, enrichedMap[Triple(0, 0, "overworld")]?.region)
        
        // (1,0, overworld) is adjacent to spawn, should be region 1
        assertEquals(1, enrichedMap[Triple(1, 0, "overworld")]?.region)
        
        // (1,1, overworld) is blocked, should be region -1
        assertEquals(-1, enrichedMap[Triple(1, 1, "overworld")]?.region)
        
        // (2,1, overworld) is NOT reachable from spawn because (1,1) is blocked
        // (0,0)->(1,0)->(2,0)->(2,1)->(2,2)->(1,2)->(0,2)->(0,1)
        // Wait, they are reachable around the block.
        // Let's check (2,1)
        // (0,0) (1,0) (2,0)
        // (0,1)  B    (2,1)
        // (0,2) (1,2) (2,2)
        // (0,0) to (2,1) path: (0,0)->(1,0)->(2,0)->(2,1) - valid.
        assertEquals(1, enrichedMap[Triple(2, 1, "overworld")]?.region)

        // Cave should be a different region
        val caveRegion = enrichedMap[Triple(0, 0, "cave")]?.region
        assertNotNull(caveRegion)
        assertNotEquals(1, caveRegion)
        assertNotEquals(-1, caveRegion)
        
        // (1,0, cave) should be same as (0,0, cave)
        assertEquals(caveRegion, enrichedMap[Triple(1, 0, "cave")]?.region)
    }

    @Test
    fun `autoEnrichMaps should separate disjoint regions on same layer`() {
        // (0,0) (1,0) (2,0)
        // (0,1)  B    (2,1)
        // (0,2) (1,2) (2,2)
        // If we also block (1,2) and (1,0), then left and right are separated.
        
        val maps = mutableListOf<MapData>()
        // 3x3 grid
        // L C R
        // L B R
        // L C R
        // Where B is blocked, C is blocked.
        for (x in 0..2) {
            for (y in 0..2) {
                val access = if (x == 1) Access("blocked", emptyList()) else null
                maps.add(createMap(x, y, "overworld", access))
            }
        }
        
        val method = MapSyncService::class.java.getDeclaredMethod("autoEnrichMaps", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val enriched = method.invoke(mapSyncService, maps) as List<MapData>
        
        val enrichedMap = enriched.associateBy { Triple(it.x, it.y, it.layer) }
        
        // Left side reachable from spawn (0,0)
        assertEquals(1, enrichedMap[Triple(0, 0, "overworld")]?.region)
        assertEquals(1, enrichedMap[Triple(0, 1, "overworld")]?.region)
        assertEquals(1, enrichedMap[Triple(0, 2, "overworld")]?.region)
        
        // Middle is blocked
        assertEquals(-1, enrichedMap[Triple(1, 0, "overworld")]?.region)
        assertEquals(-1, enrichedMap[Triple(1, 1, "overworld")]?.region)
        assertEquals(-1, enrichedMap[Triple(1, 2, "overworld")]?.region)
        
        // Right side NOT reachable from spawn
        val rightRegion = enrichedMap[Triple(2, 0, "overworld")]?.region
        assertNotNull(rightRegion)
        assertNotEquals(1, rightRegion)
        assertNotEquals(-1, rightRegion)
        
        assertEquals(rightRegion, enrichedMap[Triple(2, 1, "overworld")]?.region)
        assertEquals(rightRegion, enrichedMap[Triple(2, 2, "overworld")]?.region)
    }

    private fun createMap(x: Int, y: Int, layer: String, access: Access? = null): MapData {
        return MapData(
            name = "map_${x}_${y}",
            skin = "default",
            x = x,
            y = y,
            mapId = (x + 100) * (y + 100),
            layer = layer,
            access = access,
            interactions = null,
            region = null
        )
    }
}