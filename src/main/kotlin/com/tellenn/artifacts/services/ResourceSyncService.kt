package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.ResourceClient
import com.tellenn.artifacts.clients.models.Resource
import com.tellenn.artifacts.db.documents.ResourceDocument
import com.tellenn.artifacts.db.repositories.ResourceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
     * @return The number of resources synced
     */
    @Transactional
    fun syncAllResources(pageSize: Int = 50): Int {
        logger.info("Starting resource sync process")
        
        // Empty the database
        logger.info("Emptying resources collection")
        resourceRepository.deleteAll()
        
        var currentPage = 1
        var totalPages = 1
        var totalResourcesProcessed = 0
        
        // Fetch all pages of resources
        do {
            logger.info("Fetching resources page $currentPage of $totalPages")
            
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
                logger.info("Processed ${dataPage.size} resources from page $currentPage")
                
                currentPage++
            } catch (e: Exception) {
                logger.error("Failed to fetch resources page $currentPage", e)
                break
            }
        } while (currentPage <= totalPages)
        
        logger.info("Resource sync completed. Total resources synced: $totalResourcesProcessed")
        return totalResourcesProcessed
    }
    
    /**
     * Syncs resources for a specific skill from the API to the database.
     *
     * @param skill The skill to sync resources for (e.g., "mining", "woodcutting")
     * @param pageSize The number of resources to fetch per page (default: 50)
     * @return The number of resources synced
     */
    @Transactional
    fun syncResourcesBySkill(skill: String, pageSize: Int = 50): Int {
        logger.info("Starting resource sync process for skill: $skill")
        
        // Delete existing resources for this skill
        val existingResources = resourceRepository.findBySkill(skill)
        resourceRepository.deleteAll(existingResources)
        
        var currentPage = 1
        var totalPages = 1
        var totalResourcesProcessed = 0
        
        // Fetch all pages of resources for the skill
        do {
            logger.info("Fetching $skill resources page $currentPage of $totalPages")
            
            try {
                val response = resourceClient.getResources(
                    skill = skill,
                    page = currentPage,
                    size = pageSize
                )
                val dataPage = response.data
                totalPages = response.pages
                
                // Convert Resource to ResourceDocument and save to MongoDB in batch
                val resourceDocuments = dataPage.map { ResourceDocument.fromResource(it) }
                resourceRepository.saveAll(resourceDocuments)
                
                totalResourcesProcessed += dataPage.size
                logger.info("Processed ${dataPage.size} $skill resources from page $currentPage")
                
                currentPage++
            } catch (e: Exception) {
                logger.error("Failed to fetch $skill resources page $currentPage", e)
                break
            }
        } while (currentPage <= totalPages)
        
        logger.info("$skill resource sync completed. Total resources synced: $totalResourcesProcessed")
        return totalResourcesProcessed
    }
    
    /**
     * Syncs a single resource from the API to the database.
     *
     * @param resourceCode The code of the resource to sync
     * @return True if the sync was successful, false otherwise
     */
    @Transactional
    fun syncResource(resourceCode: String): Boolean {
        logger.info("Syncing resource with code: $resourceCode")
        
        try {
            // Fetch all resources and find the one with the matching code
            // This is a workaround since the API doesn't provide a direct endpoint to fetch a single resource
            val response = resourceClient.getResources(size = 100)
            val resource = response.data.find { it.code == resourceCode }
            
            if (resource == null) {
                logger.error("No resource found with code: $resourceCode")
                return false
            }
            
            // Convert Resource to ResourceDocument and save to MongoDB
            val resourceDocument = ResourceDocument.fromResource(resource)
            resourceRepository.save(resourceDocument)
            
            logger.info("Successfully synced resource with code: $resourceCode")
            return true
        } catch (e: Exception) {
            logger.error("Failed to sync resource with code: $resourceCode", e)
            return false
        }
    }
}