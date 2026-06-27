package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.RaidClient
import com.tellenn.artifacts.db.documents.RaidDocument
import com.tellenn.artifacts.db.repositories.RaidRepository
import com.tellenn.artifacts.utils.PaginatedSyncUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RaidSyncService(
    private val raidClient: RaidClient,
    private val raidRepository: RaidRepository,
) {
    private val logger = LoggerFactory.getLogger(RaidSyncService::class.java)

    /**
     * Backfills the raids cache when it is empty, regardless of server version.
     * Covers installs where the raids collection was introduced after the stored
     * server version, so the version-gated full sync never populated it.
     * Returns the number of raids synced, or 0 when the cache is already populated.
     */
    fun syncRaidsIfEmpty(): Int {
        if (raidRepository.count() > 0) {
            logger.debug("Raids cache already populated, skipping backfill")
            return 0
        }
        logger.info("Raids cache empty, backfilling from server")
        return syncAllRaids()
    }

    @Transactional
    fun syncAllRaids(pageSize: Int = PaginatedSyncUtils.DEFAULT_PAGE_SIZE): Int =
        PaginatedSyncUtils.syncAll(
            logger = logger,
            label = "raids",
            pageSize = pageSize,
            clearFn = raidRepository::deleteAll,
            fetchPage = { page, size -> raidClient.getRaids(page = page, size = size) },
            persistFn = { data -> raidRepository.saveAll(data.map { RaidDocument.fromRaid(it) }) },
        )
}
