package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.EffectClient
import com.tellenn.artifacts.db.repositories.EffectRepository
import com.tellenn.artifacts.utils.PaginatedSyncUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EffectSyncService(
    private val effectClient: EffectClient,
    private val effectRepository: EffectRepository,
) {
    private val logger = LoggerFactory.getLogger(EffectSyncService::class.java)

    @Transactional
    fun syncAllEffects(pageSize: Int = PaginatedSyncUtils.DEFAULT_PAGE_SIZE): Int =
        PaginatedSyncUtils.syncAll(
            logger = logger,
            label = "effects",
            pageSize = pageSize,
            clearFn = effectRepository::deleteAll,
            fetchPage = { page, size -> effectClient.getEffects(page = page, size = size) },
            persistFn = effectRepository::saveAll,
        )
}
