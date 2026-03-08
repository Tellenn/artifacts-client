package com.tellenn.artifacts.db.clients

import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.db.repositories.MapRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

/**
 * Client for interacting with the MongoDB database specifically for Map operations.
 * Provides methods to fetch and filter maps from the local database.
 */
@Component
class MapMongoClient(
    private val mapRepository: MapRepository,
    private val mongoTemplate: MongoTemplate
) {

    /**
     * Get maps from the database with filtering and pagination.
     * Supports filtering by name, content_type, content_code, and coordinates.
     */
    fun getMaps(
        name: String? = null,
        content_type: String? = null,
        content_code: String? = null,
        x: Int? = null,
        y: Int? = null,
        page: Int = 1,
        size: Int = 50
    ): ArtifactsArrayResponseBody<MapData> {
        // Create pageable object for Spring Data
        val pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "name"))

        // If we have complex filtering or need to use content filters, use MongoTemplate with Query
        if (content_type != null || content_code != null || name != null || x != null || y != null) {
            val query = Query()

            // Add name, x, y criteria if specified
            name?.let { query.addCriteria(Criteria.where("name").regex(it, "i")) }
            x?.let { query.addCriteria(Criteria.where("x").`is`(it)) }
            y?.let { query.addCriteria(Criteria.where("y").`is`(it)) }

            // Add content criteria if at least one content filter is specified
            content_type?.let { query.addCriteria(Criteria.where("interactions.content.type").`is`(it)) }
            content_code?.let { query.addCriteria(Criteria.where("interactions.content.code").`is`(it)) }
            // Apply pagination
            query.with(pageable)

            // Execute query
            val maps = mongoTemplate.find(query, MapData::class.java)
            val count = mongoTemplate.count(query.skip(-1).limit(-1), MapData::class.java)

            return ArtifactsArrayResponseBody(
                maps,
                count.toInt(),
                page,
                size,
                (count / size).toInt() + if (count % size > 0) 1 else 0
            )
        } else {
            // For simple filtering, use repository methods
            val result =  mapRepository.findAll(pageable)

            return ArtifactsArrayResponseBody(
                result.toList(),
                result.totalElements.toInt(),
                page,
                result.size,
                result.totalPages
            )
        }
    }

    /**
     * Get a map by its mapId from the database.
     *
     * @param mapId The ID of the map to find
     * @return The MapData if found, null otherwise
     */
    fun getMapById(mapId: Int): MapData? {
        return mapRepository.findByMapId(mapId)
    }

}
