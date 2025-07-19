package com.tellenn.artifacts.db.documents

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Document for storing server version information.
 * This is used to determine if sync operations need to be performed.
 * The server has a single version, and if it changes, all syncs should be performed.
 */
@Document(collection = "server_versions")
data class ServerVersionDocument(
    @Id
    val id: String = SERVER_VERSION_ID,
    val version: String,
    val lastSyncTime: Instant
) {
    companion object {
        const val SERVER_VERSION_ID = "server_version"
    }
}