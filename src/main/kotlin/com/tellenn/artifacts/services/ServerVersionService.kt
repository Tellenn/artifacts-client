package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.ServerStatusClient
import com.tellenn.artifacts.db.documents.ServerVersionDocument
import com.tellenn.artifacts.db.repositories.ServerVersionRepository
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
    private val serverStatusClient: ServerStatusClient,
    private val serverVersionRepository: ServerVersionRepository
) {
    private val logger = LoggerFactory.getLogger(ServerVersionService::class.java)

    /**
     * Get the current server version from the API.
     *
     * @return The current server version
     */
    fun getCurrentServerVersion(): String {
        return try {
            val serverStatus = serverStatusClient.getServerStatus()
            serverStatus.data.version
        } catch (e: Exception) {
            logger.error("Failed to get server version", e)
            // Return a unique value to force sync in case of error
            "error-${Instant.now()}"
        }
    }

    /**
     * Check if sync operations need to be performed.
     *
     * @param forceSync Whether to force the sync regardless of version
     * @return True if sync is needed, false otherwise
     */
    fun isSyncNeeded(forceSync: Boolean = false): Boolean {
        // Force sync if requested
        if (forceSync) {
            logger.debug("Force sync requested, sync needed")
            return true
        }

        // Get the current server version
        val currentVersion = getCurrentServerVersion()

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
    fun updateServerVersion() {
        val currentVersion = getCurrentServerVersion()
        val serverVersionDocument = ServerVersionDocument(
            version = currentVersion,
            lastSyncTime = Instant.now()
        )
        serverVersionRepository.save(serverVersionDocument)
        logger.debug("Updated server version to $currentVersion")
    }
}