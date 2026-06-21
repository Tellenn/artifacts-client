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

    private lateinit var mapSyncService: MapSyncService

    @BeforeEach
    fun setUp() {
        mapSyncService = MapSyncService(mock(MapClient::class.java), mock(MapRepository::class.java))
    }

    private val invokeAutoEnrichMaps: (List<MapData>) -> List<MapData> = { maps ->
        val method = MapSyncService::class.java.getDeclaredMethod("autoEnrichMaps", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        method.invoke(mapSyncService, maps) as List<MapData>
    }

    @Test
    fun `spawn is always region 1`() {
        val maps = listOf(createMap(0, 0, "overworld"), createMap(1, 0, "overworld"))
        val enriched = invokeAutoEnrichMaps(maps).associateBy { Triple(it.x, it.y, it.layer) }

        assertEquals(1, enriched[Triple(0, 0, "overworld")]?.region)
        assertEquals(1, enriched[Triple(1, 0, "overworld")]?.region)
    }

    @Test
    fun `adjacent open maps share the same region`() {
        // (0,0)-(1,0)-(2,0) all open → all region 1
        val maps = (0..2).map { x -> createMap(x, 0, "overworld") }
        val enriched = invokeAutoEnrichMaps(maps).associateBy { Triple(it.x, it.y, it.layer) }

        assertEquals(1, enriched[Triple(0, 0, "overworld")]?.region)
        assertEquals(1, enriched[Triple(1, 0, "overworld")]?.region)
        assertEquals(1, enriched[Triple(2, 0, "overworld")]?.region)
    }

    @Test
    fun `open map can reach around a blocked map`() {
        // (0,0) (1,0) (2,0)
        // (0,1)  B    (2,1)   ← B is blocked but path goes around via (2,0)→(2,1)
        // (0,2) (1,2) (2,2)
        val maps = mutableListOf<MapData>()
        for (x in 0..2) for (y in 0..2) {
            val access = if (x == 1 && y == 1) Access("blocked", emptyList()) else null
            maps.add(createMap(x, y, "overworld", access))
        }
        val enriched = invokeAutoEnrichMaps(maps).associateBy { Triple(it.x, it.y, it.layer) }

        assertEquals(1, enriched[Triple(0, 0, "overworld")]?.region)
        assertEquals(1, enriched[Triple(2, 1, "overworld")]?.region) // reachable around the block
    }

    @Test
    fun `blocked maps never receive region -1`() {
        // Blocked maps used to be assigned -1 but must now get a positive region
        // so that transition-based movement can find them via findTransitionPath.
        val maps = listOf(
            createMap(0, 0, "overworld"),
            createMap(1, 0, "overworld", Access("blocked", emptyList())),
            createMap(2, 0, "overworld", Access("blocked", emptyList())),
        )
        val enriched = invokeAutoEnrichMaps(maps).associateBy { Triple(it.x, it.y, it.layer) }

        val blockedRegion = enriched[Triple(1, 0, "overworld")]?.region
        assertNotNull(blockedRegion)
        assertNotEquals(-1, blockedRegion)
        // Adjacent blocked maps share the same region
        assertEquals(blockedRegion, enriched[Triple(2, 0, "overworld")]?.region)
        // Blocked region is separate from open region
        assertNotEquals(1, blockedRegion)
    }

    @Test
    fun `blocked maps form their own isolated region separate from open maps`() {
        // L B R — blocked column separates left and right open areas
        val maps = mutableListOf<MapData>()
        for (x in 0..2) for (y in 0..2) {
            val access = if (x == 1) Access("blocked", emptyList()) else null
            maps.add(createMap(x, y, "overworld", access))
        }
        val enriched = invokeAutoEnrichMaps(maps).associateBy { Triple(it.x, it.y, it.layer) }

        val leftRegion = enriched[Triple(0, 0, "overworld")]?.region   // spawn → region 1
        val blockedRegion = enriched[Triple(1, 0, "overworld")]?.region
        val rightRegion = enriched[Triple(2, 0, "overworld")]?.region

        assertEquals(1, leftRegion)
        assertNotEquals(-1, blockedRegion)
        assertNotEquals(leftRegion, blockedRegion)
        assertNotEquals(leftRegion, rightRegion)
        assertNotEquals(blockedRegion, rightRegion)

        // All blocked maps share the same region
        assertEquals(blockedRegion, enriched[Triple(1, 1, "overworld")]?.region)
        assertEquals(blockedRegion, enriched[Triple(1, 2, "overworld")]?.region)

        // All right-side maps share the same region
        assertEquals(rightRegion, enriched[Triple(2, 1, "overworld")]?.region)
        assertEquals(rightRegion, enriched[Triple(2, 2, "overworld")]?.region)
    }

    @Test
    fun `restricted maps are isolated from open maps`() {
        // (0,0) open  (1,0) restricted  (2,0) restricted
        val maps = listOf(
            createMap(0, 0, "overworld"),
            createMap(1, 0, "overworld", Access("restricted", emptyList())),
            createMap(2, 0, "overworld", Access("restricted", emptyList())),
        )
        val enriched = invokeAutoEnrichMaps(maps).associateBy { Triple(it.x, it.y, it.layer) }

        val openRegion = enriched[Triple(0, 0, "overworld")]?.region
        val restrictedRegion = enriched[Triple(1, 0, "overworld")]?.region

        assertEquals(1, openRegion)
        assertNotNull(restrictedRegion)
        assertNotEquals(-1, restrictedRegion)
        assertNotEquals(openRegion, restrictedRegion)
        // Adjacent restricted maps share the same region
        assertEquals(restrictedRegion, enriched[Triple(2, 0, "overworld")]?.region)
    }

    @Test
    fun `different layers produce distinct regions`() {
        val maps = listOf(
            createMap(0, 0, "overworld"),
            createMap(0, 0, "cave"),
            createMap(1, 0, "cave"),
        )
        val enriched = invokeAutoEnrichMaps(maps).associateBy { Triple(it.x, it.y, it.layer) }

        val overworldRegion = enriched[Triple(0, 0, "overworld")]?.region
        val caveRegion = enriched[Triple(0, 0, "cave")]?.region

        assertEquals(1, overworldRegion)
        assertNotNull(caveRegion)
        assertNotEquals(-1, caveRegion)
        assertNotEquals(overworldRegion, caveRegion)
        assertEquals(caveRegion, enriched[Triple(1, 0, "cave")]?.region)
    }

    private fun createMap(x: Int, y: Int, layer: String, access: Access? = null) = MapData(
        name = "map_${x}_${y}_${layer}",
        skin = "default",
        x = x,
        y = y,
        mapId = x * 1000 + y * 10 + layer.length,
        layer = layer,
        access = access,
        interactions = null,
        region = null,
    )
}
