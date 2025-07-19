package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.ServerStatusClient
import com.tellenn.artifacts.clients.models.ServerStatus
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.db.documents.ServerVersionDocument
import com.tellenn.artifacts.db.repositories.ServerVersionRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import java.time.Instant

class ServerVersionServiceTest {

    private lateinit var serverVersionService: ServerVersionService
    private lateinit var serverStatusClient: ServerStatusClient
    private lateinit var serverVersionRepository: ServerVersionRepository

    @BeforeEach
    fun setup() {
        // Create mocks
        serverStatusClient = Mockito.mock(ServerStatusClient::class.java)
        serverVersionRepository = Mockito.mock(ServerVersionRepository::class.java)

        // Create the service with mocked dependencies
        serverVersionService = ServerVersionService(serverStatusClient, serverVersionRepository)
    }

    @Test
    fun `should get current server version`() {
        // Given
        val serverStatus = createTestServerStatus("1.0")
        val response = ArtifactsResponseBody(serverStatus)
        `when`(serverStatusClient.getServerStatus()).thenReturn(response)

        // When
        val result = serverVersionService.getCurrentServerVersion()

        // Then
        assertEquals("1.0", result)
    }

    @Test
    fun `should handle error when getting server version`() {
        // Given
        `when`(serverStatusClient.getServerStatus()).thenThrow(RuntimeException("API Error"))

        // When
        val result = serverVersionService.getCurrentServerVersion()

        // Then
        assertTrue(result.startsWith("error-"))
    }

    @Test
    fun `should indicate sync needed when no stored version`() {
        // Given
        val serverStatus = createTestServerStatus("1.0")
        val response = ArtifactsResponseBody(serverStatus)
        `when`(serverStatusClient.getServerStatus()).thenReturn(response)
        `when`(serverVersionRepository.findByIdEquals(ServerVersionDocument.SERVER_VERSION_ID)).thenReturn(null)

        // When
        val result = serverVersionService.isSyncNeeded()

        // Then
        assertTrue(result)
    }

    @Test
    fun `should indicate sync needed when versions don't match`() {
        // Given
        val serverStatus = createTestServerStatus("1.1")
        val response = ArtifactsResponseBody(serverStatus)
        `when`(serverStatusClient.getServerStatus()).thenReturn(response)
        
        val storedVersion = ServerVersionDocument(
            version = "1.0",
            lastSyncTime = Instant.now()
        )
        `when`(serverVersionRepository.findByIdEquals(ServerVersionDocument.SERVER_VERSION_ID)).thenReturn(storedVersion)

        // When
        val result = serverVersionService.isSyncNeeded()

        // Then
        assertTrue(result)
    }

    @Test
    fun `should indicate sync not needed when versions match`() {
        // Given
        val serverStatus = createTestServerStatus("1.0")
        val response = ArtifactsResponseBody(serverStatus)
        `when`(serverStatusClient.getServerStatus()).thenReturn(response)
        
        val storedVersion = ServerVersionDocument(
            version = "1.0",
            lastSyncTime = Instant.now()
        )
        `when`(serverVersionRepository.findByIdEquals(ServerVersionDocument.SERVER_VERSION_ID)).thenReturn(storedVersion)

        // When
        val result = serverVersionService.isSyncNeeded()

        // Then
        assertFalse(result)
    }

    @Test
    fun `should indicate sync needed when forceSync is true`() {
        // Given
        val serverStatus = createTestServerStatus("1.0")
        val response = ArtifactsResponseBody(serverStatus)
        `when`(serverStatusClient.getServerStatus()).thenReturn(response)
        
        val storedVersion = ServerVersionDocument(
            version = "1.0",
            lastSyncTime = Instant.now()
        )
        `when`(serverVersionRepository.findByIdEquals(ServerVersionDocument.SERVER_VERSION_ID)).thenReturn(storedVersion)

        // When
        val result = serverVersionService.isSyncNeeded(forceSync = true)

        // Then
        assertTrue(result)
    }

    @Test
    fun `should update server version`() {
        // Given
        val serverStatus = createTestServerStatus("1.1")
        val response = ArtifactsResponseBody(serverStatus)
        `when`(serverStatusClient.getServerStatus()).thenReturn(response)

        // When
        serverVersionService.updateServerVersion()

        // Then
        verify(serverVersionRepository).save(Mockito.argThat { serverVersionDocument ->
            serverVersionDocument.id == ServerVersionDocument.SERVER_VERSION_ID &&
            serverVersionDocument.version == "1.1"
        })
    }

    private fun createTestServerStatus(version: String): ServerStatus {
        // Create a minimal ServerStatus with just the version we need for testing
        return Mockito.mock(ServerStatus::class.java).apply {
            `when`(this.version).thenReturn(version)
        }
    }
}