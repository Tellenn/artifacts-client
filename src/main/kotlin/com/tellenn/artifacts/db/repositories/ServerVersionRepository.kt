package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.ServerVersionDocument
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * Repository for accessing server version information.
 * This is used to determine if sync operations need to be performed.
 * The server has a single version, and if it changes, all syncs should be performed.
 */
@Repository
interface ServerVersionRepository : MongoRepository<ServerVersionDocument, String> {
    /**
     * Find the server version document.
     *
     * @return The server version document, or null if not found
     */
    fun findByIdEquals(id: String): ServerVersionDocument?
}