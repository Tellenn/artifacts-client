package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.ResourceClient
import com.tellenn.artifacts.db.documents.ResourceDocument
import com.tellenn.artifacts.db.repositories.ResourceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.Thread.sleep

/**
 * Service for synchronizing resources between the API and the local database.
 */
@Service
class ResourceSyncService(
    private val resourceClient: ResourceClient,
    private val resourceRepository: ResourceRepository
) {
    private val logger = LoggerFactory.getLogger(ResourceSyncService::class.java)

    /**
     * Syncs all resources from the API to the database.
     * This method will delete all existing resources in the database and replace them with the latest data from the API.
     *
     * @param pageSize The number of resources to fetch per page (default: 50)
     * @param forceSync Whether to force the sync regardless of server version (default: false)
     * @return The number of resources synced, or 0 if sync was not needed
     */
    @Transactional
    fun syncAllResources(pageSize: Int = 50, forceSync: Boolean = false): Int {
        logger.info("Starting resource sync process")
        
        // Empty the database
        logger.debug("Emptying resources collection")
        resourceRepository.deleteAll()
        
        var currentPage = 1
        var totalPages = 1
        var totalResourcesProcessed = 0
        
        // Fetch all pages of resources
        do {
            logger.debug("Fetching resources page $currentPage of $totalPages")
            
            try {
                val response = resourceClient.getResources(
                    page = currentPage,
                    size = pageSize
                )
                val dataPage = response.data
                totalPages = response.pages
                
                // Convert Resource to ResourceDocument and save to MongoDB in batch
                val resourceDocuments = dataPage.map { ResourceDocument.fromResource(it) }
                resourceRepository.saveAll(resourceDocuments)
                
                totalResourcesProcessed += dataPage.size
                logger.debug("Processed ${dataPage.size} resources from page $currentPage")
                sleep(500)
                currentPage++
            } catch (e: Exception) {
                logger.error("Failed to fetch resources page $currentPage", e)
                break
            }
        } while (currentPage <= totalPages)

        logger.info("Resource sync completed and server version updated. Total resources synced: $totalResourcesProcessed")
        return totalResourcesProcessed
    }

}