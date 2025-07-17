package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.ClientErrorDocument
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Repository for accessing and managing client error records.
 */
@Repository
interface ClientErrorRepository : MongoRepository<ClientErrorDocument, String> {
    
    /**
     * Find errors by client type.
     */
    fun findByClientType(clientType: String, pageable: Pageable): Page<ClientErrorDocument>
    
    /**
     * Find errors by endpoint.
     */
    fun findByEndpoint(endpoint: String, pageable: Pageable): Page<ClientErrorDocument>
    
    /**
     * Find errors by error code.
     */
    fun findByErrorCode(errorCode: Int, pageable: Pageable): Page<ClientErrorDocument>
    
    /**
     * Find errors that occurred after a specific timestamp.
     */
    fun findByTimestampAfter(timestamp: Instant, pageable: Pageable): Page<ClientErrorDocument>
    
    /**
     * Find errors that occurred before a specific timestamp.
     */
    fun findByTimestampBefore(timestamp: Instant, pageable: Pageable): Page<ClientErrorDocument>
    
    /**
     * Find errors that occurred between two timestamps.
     */
    fun findByTimestampBetween(startTime: Instant, endTime: Instant, pageable: Pageable): Page<ClientErrorDocument>
}