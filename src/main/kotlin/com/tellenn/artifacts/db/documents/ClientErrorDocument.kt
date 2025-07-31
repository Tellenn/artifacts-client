package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.models.ArtifactsCharacter
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import org.bson.types.ObjectId

/**
 * Document for storing client error information.
 * Records details about API request errors for debugging and monitoring.
 */
@Document(collection = "clientErrors")
data class ClientErrorDocument(
    @Id
    val id: String = ObjectId().toString(),
    val clientType: String,
    val endpoint: String,
    val requestMethod: String,
    val requestParams: String,
    val requestBody: String?,
    val responseBody: String?,
    val errorCode: Int,
    val errorMessage: String,
    val character: ArtifactsCharacter?,
    val stackTrace: String,
    val timestamp: Instant = Instant.now()
) {
    companion object {
        /**
         * Creates a ClientErrorDocument from error details.
         *
         * @param clientType The type of client that encountered the error
         * @param endpoint The API endpoint that was called
         * @param requestMethod The HTTP method used (GET, POST, etc.)
         * @param requestParams The query parameters or path variables
         * @param requestBody The request body, if any
         * @param responseBody The response body, if any
         * @param errorCode The HTTP error code
         * @param errorMessage The error message
         * @return A new ClientErrorDocument
         */
        fun fromErrorDetails(
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
            return ClientErrorDocument(
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
        }
    }
}