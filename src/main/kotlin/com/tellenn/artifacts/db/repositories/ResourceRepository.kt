package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.ResourceDocument
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for accessing and managing resources in the database.
 */
@Repository
interface ResourceRepository : MongoRepository<ResourceDocument, String> {

    /**
     * Find resources by skill type.
     *
     * @param skill The skill type to filter by (e.g., "mining", "woodcutting")
     * @return List of resources for the specified skill
     */
    fun findBySkill(skill: String): List<ResourceDocument>

    /**
     * Find resources by skill type and with level less than or equal to the specified level.
     *
     * @param skill The skill type to filter by
     * @param level The maximum level to filter by
     * @return List of resources for the specified skill with level <= the specified level
     */
    fun findBySkillAndLevelLessThanEqual(skill: String, level: Int): List<ResourceDocument>

    /**
     * Find resources by skill type and with level equal to the specified level.
     *
     * @param skill The skill type to filter by
     * @param level The exact level to filter by
     * @return List of resources for the specified skill with the specified level
     */
    fun findBySkillAndLevel(skill: String, level: Int): List<ResourceDocument>

    /**
     * Find a resource by its code.
     *
     * @param code The resource code
     * @return The resource with the specified code, or null if not found
     */
    fun findByCode(code: String): ResourceDocument

    fun findByDropsCode(code: String): ResourceDocument
}