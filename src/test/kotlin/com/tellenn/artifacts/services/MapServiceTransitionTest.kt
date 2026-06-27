package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.MapClient
import com.tellenn.artifacts.db.clients.MapMongoClient
import com.tellenn.artifacts.db.repositories.TransitionMapperRepository
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.models.TransitionMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class MapServiceTransitionTest {

    private lateinit var transitionMapperRepository: TransitionMapperRepository
    private lateinit var mapMongoClient: MapMongoClient
    private lateinit var accountClient: AccountClient
    private lateinit var mapService: MapService

    @BeforeEach
    fun setUp() {
        mapMongoClient = mock(MapMongoClient::class.java)
        transitionMapperRepository = mock(TransitionMapperRepository::class.java)
        accountClient = mock(AccountClient::class.java)
        mapService = MapService(mapMongoClient, transitionMapperRepository, accountClient, mock(MapClient::class.java))
    }

    @Test
    fun `findTransitionPath should return empty list when origin is destination`() {
        val result = mapService.findTransitionPath(1, 1)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findTransitionPath should return direct path`() {
        val t1 = createTransition(1, 2)
        `when`(transitionMapperRepository.findBySourceMapDataRegion(1)).thenReturn(listOf(t1))

        val result = mapService.findTransitionPath(1, 2)
        assertEquals(1, result.size)
        assertEquals(t1, result[0])
    }

    @Test
    fun `findTransitionPath should find shortest path with multiple steps`() {
        // 1 -> 2 -> 4
        // 1 -> 3 -> 4 (but 1->3, 3->4)
        val t12 = createTransition(1, 2)
        val t13 = createTransition(1, 3)
        val t24 = createTransition(2, 4)
        val t34 = createTransition(3, 4)

        `when`(transitionMapperRepository.findBySourceMapDataRegion(1)).thenReturn(listOf(t12, t13))
        `when`(transitionMapperRepository.findBySourceMapDataRegion(2)).thenReturn(listOf(t24))
        `when`(transitionMapperRepository.findBySourceMapDataRegion(3)).thenReturn(listOf(t34))

        val result = mapService.findTransitionPath(1, 4)
        assertEquals(2, result.size)
        // Since we use BFS, it should find one of the 2-step paths.
        assertTrue(result == listOf(t12, t24) || result == listOf(t13, t34))
    }

    @Test
    fun `findTransitionPath should find the shortest path when paths have different lengths`() {
        // 1 -> 2 -> 3 -> 4
        // 1 -> 5 -> 4
        val t12 = createTransition(1, 2)
        val t23 = createTransition(2, 3)
        val t34 = createTransition(3, 4)
        val t15 = createTransition(1, 5)
        val t54 = createTransition(5, 4)

        `when`(transitionMapperRepository.findBySourceMapDataRegion(1)).thenReturn(listOf(t12, t15))
        `when`(transitionMapperRepository.findBySourceMapDataRegion(2)).thenReturn(listOf(t23))
        `when`(transitionMapperRepository.findBySourceMapDataRegion(3)).thenReturn(listOf(t34))
        `when`(transitionMapperRepository.findBySourceMapDataRegion(5)).thenReturn(listOf(t54))

        val result = mapService.findTransitionPath(1, 4)
        assertEquals(2, result.size)
        assertEquals(listOf(t15, t54), result)
    }

    @Test
    fun `findTransitionPath should handle loops`() {
        // 1 -> 2 -> 1
        // 1 -> 3
        val t12 = createTransition(1, 2)
        val t21 = createTransition(2, 1)
        val t13 = createTransition(1, 3)

        `when`(transitionMapperRepository.findBySourceMapDataRegion(1)).thenReturn(listOf(t12, t13))
        `when`(transitionMapperRepository.findBySourceMapDataRegion(2)).thenReturn(listOf(t21))

        val result = mapService.findTransitionPath(1, 3)
        assertEquals(1, result.size)
        assertEquals(t13, result[0])
    }

    @Test
    fun `findTransitionPath should return empty list if no path exists`() {
        `when`(transitionMapperRepository.findBySourceMapDataRegion(1)).thenReturn(emptyList())

        val result = mapService.findTransitionPath(1, 2)
        assertTrue(result.isEmpty())
    }

    private fun createTransition(source: Int, destination: Int): TransitionMapper {
        return TransitionMapper(
            id = "t_${source}_${destination}",
            sourceMapData = createMapData(source),
            destinationMapData = createMapData(destination),
            conditions = null
        )
    }

    private fun createMapData(region: Int): MapData {
        return MapData(
            name = "map_region_$region",
            skin = "default",
            x = 0,
            y = 0,
            mapId = region,
            layer = "main",
            access = null,
            interactions = null,
            region = region
        )
    }
}
