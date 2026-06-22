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
