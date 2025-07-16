package com.tellenn.artifacts.db.clients

import com.tellenn.artifacts.clients.models.MapData
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.db.documents.MapDocument
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
            if (content_type != null || content_code != null) {
                content_type?.let { query.addCriteria(Criteria.where("content.type").`is`(it)) }
                content_code?.let { query.addCriteria(Criteria.where("content.code").`is`(it)) }
            }

            // Apply pagination
            query.with(pageable)

            // Execute query
            val maps = mongoTemplate.find(query, MapDocument::class.java)
            val count = mongoTemplate.count(query.skip(-1).limit(-1), MapDocument::class.java)

            return ArtifactsArrayResponseBody(
                maps.map { convertToMapData(it) },
                count.toInt(),
                page,
                size,
                (count / size).toInt() + if (count % size > 0) 1 else 0
            )
        } else {
            // For simple filtering, use repository methods
            val result = when {
                name != null -> mapRepository.findAll(pageable) // Replace with appropriate method if available
                x != null && y != null -> mapRepository.findByXAndY(x, y, pageable)
                x != null -> mapRepository.findByX(x, pageable)
                y != null -> mapRepository.findByY(y, pageable)
                else -> mapRepository.findAll(pageable)
            }

            return ArtifactsArrayResponseBody(
                result.map { convertToMapData(it) }.toList(),
                result.totalElements.toInt(),
                page,
                result.size,
                result.totalPages
            )
        }
    }

    /**
     * Get map details by coordinates.
     */
    fun getMap(x: Int, y: Int): ArtifactsResponseBody<MapData> {
        val id = "${x}_${y}"
        val mapDocument = mapRepository.findById(id)
            .orElseThrow { NoSuchElementException("Map at coordinates ($x, $y) not found") }

        return ArtifactsResponseBody(convertToMapData(mapDocument))
    }

    /**
     * Convert MapDocument to MapData.
     */
    private fun convertToMapData(mapDocument: MapDocument): MapData {
        return MapData(
            skin = mapDocument.skin,
            x = mapDocument.x,
            y = mapDocument.y,
            content = mapDocument.content?.let {
                com.tellenn.artifacts.clients.models.MapContent(
                    type = it.type,
                    code = it.code
                )
            }
        )
    }
}
