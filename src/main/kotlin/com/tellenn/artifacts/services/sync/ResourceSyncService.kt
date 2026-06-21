package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.ResourceClient
import com.tellenn.artifacts.db.documents.ResourceDocument
import com.tellenn.artifacts.db.repositories.ResourceRepository
import com.tellenn.artifacts.utils.PaginatedSyncUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ResourceSyncService(
    private val resourceClient: ResourceClient,
    private val resourceRepository: ResourceRepository,
) {
    private val logger = LoggerFactory.getLogger(ResourceSyncService::class.java)

    @Transactional
    fun syncAllResources(pageSize: Int = PaginatedSyncUtils.DEFAULT_PAGE_SIZE, forceSync: Boolean = false): Int =
        PaginatedSyncUtils.syncAll(
            logger = logger,
            label = "resources",
            pageSize = pageSize,
            clearFn = resourceRepository::deleteAll,
            fetchPage = { page, size -> resourceClient.getResources(page = page, size = size) },
            persistFn = { data -> resourceRepository.saveAll(data.map { ResourceDocument.fromResource(it) }) },
        )
}
