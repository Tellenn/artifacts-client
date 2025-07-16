package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.MapData
import com.tellenn.artifacts.db.clients.MapMongoClient
import com.tellenn.artifacts.exceptions.UnknownMapException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Service for finding maps in proximity to a character.
 * Provides methods to find the closest map to a character.
 */
@Service
class MapProximityService(
    private val mapMongoClient: MapMongoClient
) {
    private val logger = LoggerFactory.getLogger(MapProximityService::class.java)

    /**
     * Finds the closest map to a character.
     *
     * @param character The character to find the closest map to
     * @param contentType Optional filter for map content type
     * @param contentCode Optional filter for map content code
     * @return A Pair of x and y coordinates of the closest map, or null if no maps are found
     */
    fun findClosestMap(
        character: ArtifactsCharacter,
        contentType: String? = null,
        contentCode: String? = null
    ): MapData {
        logger.info("Finding closest map to character ${character.name} at position (${character.x}, ${character.y})")

        // Fetch maps from the database
        val mapsResponse = mapMongoClient.getMaps(
            page = 1,
            size = 100, // Fetch a reasonable number of maps to compare
            content_type = contentType.takeIf { it != null },
            content_code = contentCode.takeIf { it != null }
        )

        if (mapsResponse.data.isEmpty()) {
            logger.warn("No maps found with the specified criteria")
            throw UnknownMapException(contentType, contentCode)
        }

        // Find the closest map
        val closestMap = findClosestMapToCharacter(character, mapsResponse.data)


        logger.info("Closest map to character ${character.name} is at position (${closestMap.x}, ${closestMap.y})")
        return closestMap
    }

    /**
     * Calculates the Euclidean distance between a character and a map.
     *
     * @param character The character
     * @param map The map
     * @return The distance between the character and the map
     */
    private fun calculateDistance(character: ArtifactsCharacter, map: MapData): Double {
        val dx = (character.x - map.x).toDouble()
        val dy = (character.y - map.y).toDouble()
        return sqrt(dx.pow(2) + dy.pow(2))
    }

    /**
     * Finds the closest map to a character from a list of maps.
     *
     * @param character The character
     * @param maps The list of maps to search
     * @return The closest map, or null if the list is empty
     */
    private fun findClosestMapToCharacter(character: ArtifactsCharacter, maps: List<MapData>): MapData {
        return maps.minByOrNull { calculateDistance(character, it) }!!
    }
}
