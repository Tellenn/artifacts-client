package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.MonsterClient
import com.tellenn.artifacts.db.repositories.MonsterRepository
import com.tellenn.artifacts.utils.PaginatedSyncUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MonsterSyncService(
    private val monsterClient: MonsterClient,
    private val monsterRepository: MonsterRepository,
) {
    private val logger = LoggerFactory.getLogger(MonsterSyncService::class.java)

    @Transactional
    fun syncAllMonsters(pageSize: Int = PaginatedSyncUtils.DEFAULT_PAGE_SIZE, forceSync: Boolean = false): Int =
        PaginatedSyncUtils.syncAll(
            logger = logger,
            label = "monsters",
            pageSize = pageSize,
            clearFn = monsterRepository::deleteAll,
            fetchPage = { page, size -> monsterClient.getMonsters(page = page, size = size) },
            persistFn = monsterRepository::saveAll,
        )
}
