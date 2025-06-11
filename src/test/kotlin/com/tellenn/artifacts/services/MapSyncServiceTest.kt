package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.MapClient
import com.tellenn.artifacts.clients.models.MapData
import com.tellenn.artifacts.clients.models.MapContent
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.db.repositories.MapRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString

class MapSyncServiceTest {

    private lateinit var mapSyncService: MapSyncService
    private lateinit var mapRepository: MapRepository
    private lateinit var mapClient: MapClient

    @BeforeEach
    fun setup() {
        // Create mocks
        mapRepository = Mockito.mock(MapRepository::class.java)
        mapClient = Mockito.mock(MapClient::class.java)

        // Create the service with mocked dependencies
        mapSyncService = MapSyncService(mapClient, mapRepository)
    }

    @Test
    fun `should sync a single map chunk`() {
        // Given
        val testMapData = createTestMapData(0, 0, "map_0_0", "default")
        val response = ArtifactsResponseBody(testMapData)

        `when`(mapClient.getMap(0, 0, "map_0_0", "default")).thenReturn(response)

        // When
        val result = mapSyncService.syncMapChunk(0, 0, "map_0_0", "default")

        // Then
        assertTrue(result)

        // Verify that the repository's save method was called with a MapDocument
        verify(mapRepository).save(Mockito.argThat { mapDocument ->
            mapDocument.id == "0_0" &&
            mapDocument.name == "map_0_0" &&
            mapDocument.skin == "default" &&
            mapDocument.x == 0 &&
            mapDocument.y == 0 &&
            mapDocument.content.type == "monster" &&
            mapDocument.content.code == "dragon"
        })
    }

    @Test
    fun `should sync multiple map chunks in an area`() {
        // Given
        val testMapData1 = createTestMapData(0, 0, "map_0_0", "default")
        val response1 = ArtifactsResponseBody(testMapData1)

        // Create a mock that returns a response for any call
        doAnswer { response1 }.`when`(mapClient).getMap(anyInt(), anyInt(), anyString(), anyString())

        // When
        val result = mapSyncService.syncMapArea(0, 0, 20, 20, 10)

        // Then
        assertEquals(4, result)

        // Verify that deleteAll was called to clear the repository
        verify(mapRepository).deleteAll()

        // Verify that save was called for each map chunk
        verify(mapRepository, times(4)).save(Mockito.any())
    }

    @Test
    fun `should handle API error when syncing map chunk`() {
        // Given
        `when`(mapClient.getMap(0, 0, "map_0_0", "default")).thenThrow(RuntimeException("API Error"))

        // When
        val result = mapSyncService.syncMapChunk(0, 0, "map_0_0", "default")

        // Then
        assertFalse(result)

        // Verify that save was never called
        verify(mapRepository, times(0)).save(Mockito.any())
    }

    @Test
    fun `should handle API error when syncing map area`() {
        // Given
        val testMapData1 = createTestMapData(0, 0, "map_0_0", "default")
        val response1 = ArtifactsResponseBody(testMapData1)

        // Create a mock that returns a response for any call except for (10, 0)
        doAnswer { invocation ->
            val x = invocation.getArgument<Int>(0)
            val y = invocation.getArgument<Int>(1)
            
            if (x == 10 && y == 0) {
                throw RuntimeException("API Error")
            }
            
            response1
        }.`when`(mapClient).getMap(anyInt(), anyInt(), anyString(), anyString())

        // When
        val result = mapSyncService.syncMapArea(0, 0, 20, 20, 10)

        // Then
        assertEquals(3, result)

        // Verify that deleteAll was called to clear the repository
        verify(mapRepository).deleteAll()

        // Verify that save was called for the successful map chunks
        verify(mapRepository, times(3)).save(Mockito.any())
    }

    private fun createTestMapData(x: Int, y: Int, name: String = "default", skin: String = "default"): MapData {
        return MapData(
            name = name,
            skin = skin,
            x = x,
            y = y,
            content = MapContent(
                type = "monster",
                code = "dragon"
            )
        )
    }
}