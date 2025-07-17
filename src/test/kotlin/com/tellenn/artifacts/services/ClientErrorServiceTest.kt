package com.tellenn.artifacts.services

import com.tellenn.artifacts.db.documents.ClientErrorDocument
import com.tellenn.artifacts.db.repositories.ClientErrorRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class ClientErrorServiceTest {

    @Mock
    private lateinit var clientErrorRepository: ClientErrorRepository

    private lateinit var clientErrorService: ClientErrorService

    @BeforeEach
    fun setUp() {
        clientErrorService = ClientErrorService(clientErrorRepository)
    }

    @Test
    fun `test logError saves error document to repository`() {
        // Arrange
        val clientType = "TestClient"
        val endpoint = "/test/endpoint"
        val requestMethod = "GET"
        val requestParams = "param1=value1"
        val requestBody = null
        val responseBody = "Error response"
        val errorCode = 404
        val errorMessage = "Not Found"

        val savedDocument = ClientErrorDocument(
            clientType = clientType,
            endpoint = endpoint,
            requestMethod = requestMethod,
            requestParams = requestParams,
            requestBody = requestBody,
            responseBody = responseBody,
            errorCode = errorCode,
            errorMessage = errorMessage
        )

        `when`(clientErrorRepository.save(Mockito.any(ClientErrorDocument::class.java)))
            .thenReturn(savedDocument)

        // Act
        val result = clientErrorService.logError(
            clientType = clientType,
            endpoint = endpoint,
            requestMethod = requestMethod,
            requestParams = requestParams,
            requestBody = requestBody,
            responseBody = responseBody,
            errorCode = errorCode,
            errorMessage = errorMessage
        )

        // Assert
        verify(clientErrorRepository).save(Mockito.any(ClientErrorDocument::class.java))
        assert(result.clientType == clientType)
        assert(result.endpoint == endpoint)
        assert(result.requestMethod == requestMethod)
        assert(result.requestParams == requestParams)
        assert(result.requestBody == requestBody)
        assert(result.responseBody == responseBody)
        assert(result.errorCode == errorCode)
        assert(result.errorMessage == errorMessage)
    }

    @Test
    fun `test getRecentErrors returns errors sorted by timestamp`() {
        // Arrange
        val now = Instant.now()
        val error1 = ClientErrorDocument(
            clientType = "TestClient",
            endpoint = "/test/1",
            requestMethod = "GET",
            requestParams = "",
            requestBody = null,
            responseBody = null,
            errorCode = 404,
            errorMessage = "Error 1",
            timestamp = now.minusSeconds(10)
        )
        val error2 = ClientErrorDocument(
            clientType = "TestClient",
            endpoint = "/test/2",
            requestMethod = "GET",
            requestParams = "",
            requestBody = null,
            responseBody = null,
            errorCode = 500,
            errorMessage = "Error 2",
            timestamp = now
        )

        `when`(clientErrorRepository.findAll()).thenReturn(listOf(error1, error2))

        // Act
        val result = clientErrorService.getRecentErrors(10)

        // Assert
        assert(result.size == 2)
        assert(result[0].timestamp == now) // Most recent first
        assert(result[1].timestamp == now.minusSeconds(10))
    }
}
