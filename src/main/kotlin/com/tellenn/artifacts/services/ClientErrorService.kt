package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.db.documents.ClientErrorDocument
import com.tellenn.artifacts.db.repositories.ClientErrorRepository
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Service for logging client errors to the database.
 * Provides methods to record API request errors for debugging and monitoring.
 */
@Service
class ClientErrorService(
    private val clientErrorRepository: ClientErrorRepository
) {
    private val logger = LoggerFactory.getLogger(ClientErrorService::class.java)

    /**
     * Logs an error that occurred during an API request.
     *
     * @param clientType The type of client that encountered the error
     * @param endpoint The API endpoint that was called
     * @param requestMethod The HTTP method used (GET, POST, etc.)
     * @param requestParams The query parameters or path variables
     * @param requestBody The request body, if any
     * @param responseBody The response body, if any
     * @param errorCode The HTTP error code
     * @param errorMessage The error message
     * @return The saved ClientErrorDocument
     */
    fun logError(
        clientType: String,
        endpoint: String,
        requestMethod: String,
        requestParams: String,
        requestBody: String?,
        responseBody: String?,
        errorCode: Int,
        errorMessage: String,
        character: ArtifactsCharacter?,
        stackTrace: String
    ): ClientErrorDocument {
        logger.error("Client error: [$clientType] $requestMethod $endpoint - $errorCode: $responseBody")

        val errorDocument = ClientErrorDocument.fromErrorDetails(
            clientType = clientType,
            endpoint = endpoint,
            requestMethod = requestMethod,
            requestParams = requestParams,
            requestBody = requestBody,
            responseBody = responseBody,
            errorCode = errorCode,
            errorMessage = errorMessage,
            character = character,
            stackTrace = stackTrace
        )
        
        return clientErrorRepository.save(errorDocument)
    }

    /**
     * Gets recent errors, sorted by timestamp in descending order.
     *
     * @param limit The maximum number of errors to return
     * @return A list of recent errors
     */
    fun getRecentErrors(limit: Int): List<ClientErrorDocument> {
        return clientErrorRepository.findAll()
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    /**
     * Gets errors that occurred within a specific time range.
     *
     * @param startTime The start of the time range
     * @param endTime The end of the time range
     * @return A list of errors within the time range
     */
    fun getErrorsInTimeRange(startTime: Instant, endTime: Instant): List<ClientErrorDocument> {
        return clientErrorRepository.findByTimestampBetween(startTime, endTime, org.springframework.data.domain.PageRequest.of(0, 1000))
            .content
    }
}