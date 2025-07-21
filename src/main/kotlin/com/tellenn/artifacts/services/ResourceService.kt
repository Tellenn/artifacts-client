package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.MapData
import com.tellenn.artifacts.db.clients.MapMongoClient
import com.tellenn.artifacts.db.documents.ResourceDocument
import com.tellenn.artifacts.db.repositories.ResourceRepository
import com.tellenn.artifacts.exceptions.UnknownMapException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Collections.reverseOrder

/**
 * Service for working with resources.
 * Provides methods to find resources based on character skills and levels.
 */
@Service
class ResourceService(
    private val mapProximityService: MapProximityService,
    private val mapMongoClient: MapMongoClient,
    private val resourceRepository: ResourceRepository
) {
    private val logger = LoggerFactory.getLogger(ResourceService::class.java)

    /**
     * Finds the closest map with a resource of the specified skill type that the character can gather.
     * Returns the highest level resource that the character can gather, prioritizing higher level resources.
     *
     * @param character The character to find the closest resource for
     * @param skillType The type of skill (e.g., "mining", "woodcutting")
     * @return The closest map with a resource of the specified type
     * @throws IllegalArgumentException if the skill type is not supported
     * @throws UnknownMapException if no map with the specified resource is found
     */
    fun findClosestMapWithResource(character: ArtifactsCharacter, skillType: String): MapData {
        logger.debug("Finding closest map with ${skillType} resource for character ${character.name}")

        val characterLevel = character.getLevelOf(skillType)

        logger.debug("Character ${character.name} has ${skillType} level $characterLevel")

        // Get all resources for this skill that the character can gather, sorted by level in descending order
        val availableResources = resourceRepository.findBySkillAndLevelLessThanEqual(skillType, characterLevel)
            .sortedByDescending { it.level }

        if (availableResources.isEmpty()) {
            logger.error("No resources found for skill type: $skillType")
            throw IllegalArgumentException("No resources found for skill type: $skillType")
        }

        // Determine the content type based on the skill type
        // Try each resource in descending order of level (highest level first)
        for (resource in availableResources) {
            try {
                logger.debug("Trying to find map with resource: ${resource.code}")
                val map = mapProximityService.findClosestMap(
                    character = character, 
                    contentType = "resource",
                    contentCode = resource.code
                )
                logger.debug("Found map with resource ${resource.code} at (${map.x}, ${map.y})")
                return map
            } catch (e: UnknownMapException) {
                logger.debug("No map found with resource: ${resource.code}, trying next resource")
                continue
            }
        }

        // If we get here, no map was found for any resource level
        logger.error("No map found with any ${skillType} resource for character ${character.name}")
        throw UnknownMapException("resource", null)
    }

    /**
     * Gets all resources for a specific skill type.
     *
     * @param skillType The type of skill (e.g., "mining", "woodcutting")
     * @return List of resources for the specified skill
     */
    fun getResourcesBySkill(skillType: String): List<com.tellenn.artifacts.clients.models.Resource> {
        logger.info("Getting resources for skill: $skillType")
        val resources = resourceRepository.findBySkill(skillType)
        return resources.map { ResourceDocument.toResource(it) }
    }

    /**
     * Gets all resources for a specific skill type up to a maximum level.
     *
     * @param skillType The type of skill (e.g., "mining", "woodcutting")
     * @param maxLevel The maximum level of resources to include
     * @return List of resources for the specified skill up to the maximum level
     */
    fun getResourcesBySkillAndMaxLevel(skillType: String, maxLevel: Int): List<com.tellenn.artifacts.clients.models.Resource> {
        logger.info("Getting resources for skill: $skillType with max level: $maxLevel")
        val resources = resourceRepository.findBySkillAndLevelLessThanEqual(skillType, maxLevel)
        return resources.map { ResourceDocument.toResource(it) }
    }
}
