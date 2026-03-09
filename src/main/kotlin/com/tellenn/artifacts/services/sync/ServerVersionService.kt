package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.db.documents.ServerVersionDocument
import com.tellenn.artifacts.db.repositories.ServerVersionRepository
import com.tellenn.artifacts.models.ServerStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Service for handling server version operations.
 * This is used to determine if sync operations need to be performed.
 * The server has a single version, and if it changes, all syncs should be performed.
 */
@Service
class ServerVersionService(
    private val serverVersionRepository: ServerVersionRepository
) {
    private val logger = LoggerFactory.getLogger(ServerVersionService::class.java)

    /**
     * Check if sync operations need to be performed.
     *
     * @param serverStatus The server status from the API
     * @return True if sync is needed, false otherwise
     */
    fun isSyncNeeded(serverStatus: ServerStatus): Boolean {

        // Get the current server version
        val currentVersion = serverStatus.version

        // Get the stored server version
        val storedVersion = serverVersionRepository.findByIdEquals(ServerVersionDocument.SERVER_VERSION_ID)

        // If no stored version, sync is needed
        if (storedVersion == null) {
            logger.debug("No stored version, sync needed")
            return true
        }

        // If versions don't match, sync is needed
        val syncNeeded = storedVersion.version != currentVersion
        if (syncNeeded) {
            logger.debug("Server version changed (${storedVersion.version} -> $currentVersion), sync needed")
        } else {
            logger.debug("Server version unchanged ($currentVersion), sync not needed")
        }

        return syncNeeded
    }

    /**
     * Update the stored server version after sync operations.
     */
    fun updateServerVersion(serverStatus: ServerStatus) {
        val currentVersion = serverStatus.version
        val serverVersionDocument = ServerVersionDocument(
            version = currentVersion,
            lastSyncTime = Instant.now()
        )
        serverVersionRepository.save(serverVersionDocument)
        logger.debug("Updated server version to $currentVersion")
    }
}