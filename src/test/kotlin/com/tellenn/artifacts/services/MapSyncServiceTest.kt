package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.MapClient
import com.tellenn.artifacts.clients.models.MapData
import com.tellenn.artifacts.clients.models.MapContent
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.db.documents.ServerVersionDocument
import com.tellenn.artifacts.db.repositories.MapRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.times

class MapSyncServiceTest {

    private lateinit var mapSyncService: MapSyncService
    private lateinit var mapRepository: MapRepository
    private lateinit var mapClient: MapClient
    private lateinit var serverVersionService: ServerVersionService

    @BeforeEach
    fun setup() {
        // Create mocks
        mapRepository = Mockito.mock(MapRepository::class.java)
        mapClient = Mockito.mock(MapClient::class.java)
        serverVersionService = Mockito.mock(ServerVersionService::class.java)

        // Create the service with mocked dependencies
        mapSyncService = MapSyncService(mapClient, mapRepository, serverVersionService)
    }

    @Test
    fun `should sync a single map chunk`() {
        // Given
        val testMapData = createTestMapData(0, 0, "map_0_0", "default")

        val response = ArtifactsArrayResponseBody(listOf(testMapData),1,1,1,1,)

        `when`(mapClient.getMaps(
            name = "map_0_0",
            content_type = null,
            content_code = null,
            page = 1,
            size = 1
        )).thenReturn(response)

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
            mapDocument.content?.type == "monster" &&
            mapDocument.content?.code == "dragon"
        })

        // Verify that the server version was updated after successful sync
        verify(serverVersionService).updateServerVersion()
    }
    
    // These tests are no longer needed as server version checking is now done in MainRuntime

    @Test
    fun `should sync the whole map`() {
        // Given
        val testMapData1 = createTestMapData(0, 0, "map_0_0", "default")
        val testMapData2 = createTestMapData(10, 0, "map_10_0", "default")
        val testMapData3 = createTestMapData(0, 10, "map_0_10", "default")
        val testMapData4 = createTestMapData(10, 10, "map_10_10", "default")

        val response = ArtifactsArrayResponseBody(listOf(testMapData1, testMapData2, testMapData3, testMapData4),1,1,1,1)

        // Create a mock that returns a response for the getMaps call
        `when`(mapClient.getMaps(
            content_type = null,
            content_code = null,
            page = 1,
            size = 10
        )).thenReturn(response)

        // When
        val result = mapSyncService.syncWholeMap(10)

        // Then
        assertEquals(4, result)

        // Verify that deleteAll was called to clear the repository
        verify(mapRepository).deleteAll()

        // Verify that saveAll was called with the list of map documents
        verify(mapRepository).saveAll(Mockito.anyList())
        
        // Verify that the server version was updated after successful sync
        verify(serverVersionService).updateServerVersion()
    }
    
    // These tests are no longer needed as server version checking is now done in MainRuntime

    @Test
    fun `should sync multiple map chunks in an area using deprecated method`() {
        // Given
        val testMapData1 = createTestMapData(0, 0, "map_0_0", "default")
        val testMapData2 = createTestMapData(10, 0, "map_10_0", "default")
        val testMapData3 = createTestMapData(0, 10, "map_0_10", "default")
        val testMapData4 = createTestMapData(10, 10, "map_10_10", "default")

        val response = ArtifactsArrayResponseBody(listOf(testMapData1, testMapData2, testMapData3, testMapData4),1,1,1,1)

        // Create a mock that returns a response for the getMaps call
        `when`(mapClient.getMaps(
            content_type = null,
            content_code = null,
            page = 1,
            size = 50
        )).thenReturn(response)

        // When
        val result = mapSyncService.syncWholeMap()

        // Then
        assertEquals(4, result)

        // Verify that deleteAll was called to clear the repository
        verify(mapRepository).deleteAll()

        // Verify that saveAll was called with the list of map documents
        verify(mapRepository).saveAll(Mockito.anyList())
        
        // Verify that the server version was updated after successful sync
        verify(serverVersionService).updateServerVersion()
    }

    @Test
    fun `should handle API error when syncing map chunk`() {
        // Given
        `when`(mapClient.getMaps(
            name = "map_0_0",
            content_type = null,
            content_code = null,
            page = 1,
            size = 1
        )).thenThrow(RuntimeException("API Error"))

        // When
        val result = mapSyncService.syncMapChunk(0, 0, "map_0_0", "default")

        // Then
        assertFalse(result)

        // Verify that save was never called
        verify(mapRepository, times(0)).save(Mockito.any())
    }

    @Test
    fun `should handle API error when syncing whole map`() {
        // Given
        val testMapData1 = createTestMapData(0, 0, "map_0_0", "default")
        val testMapData2 = createTestMapData(10, 0, "map_10_0", "default")
        val testMapData3 = createTestMapData(0, 10, "map_0_10", "default")

        val response1 = ArtifactsArrayResponseBody(listOf(testMapData1, testMapData2, testMapData3),1,1,1,1)

        // First page succeeds
        `when`(mapClient.getMaps(
            content_type = null,
            content_code = null,
            page = 1,
            size = 10
        )).thenReturn(response1)

        // Second page fails
        `when`(mapClient.getMaps(
            content_type = null,
            content_code = null,
            page = 2,
            size = 10
        )).thenThrow(RuntimeException("API Error"))

        // When
        val result = mapSyncService.syncWholeMap(10)

        // Then
        assertEquals(3, result)

        // Verify that deleteAll was called to clear the repository
        verify(mapRepository).deleteAll()

        // Verify that saveAll was called for the successful map chunks
        verify(mapRepository).saveAll(Mockito.anyList())
        
        // Verify that the server version was updated even with partial success
        verify(serverVersionService).updateServerVersion()
    }
    
    @Test
    fun `should handle API error when syncing map chunk and not update version`() {
        // Given
        `when`(mapClient.getMaps(
            name = "map_0_0",
            content_type = null,
            content_code = null,
            page = 1,
            size = 1
        )).thenThrow(RuntimeException("API Error"))

        // When
        val result = mapSyncService.syncMapChunk(0, 0, "map_0_0", "default")

        // Then
        assertFalse(result)

        // Verify that save was never called
        verify(mapRepository, times(0)).save(Mockito.any())
        
        // Verify that the server version was not updated due to error
        verify(serverVersionService, times(0)).updateServerVersion()
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