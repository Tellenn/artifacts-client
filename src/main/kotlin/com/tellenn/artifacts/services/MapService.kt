package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.MapClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.db.clients.MapMongoClient
import com.tellenn.artifacts.exceptions.UnknownMapException
import com.tellenn.artifacts.db.repositories.TransitionMapperRepository
import com.tellenn.artifacts.models.TransitionMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Service for finding maps close to a character.
 * Provides methods to find the closest map to a character.
 */
@Service
class MapService(
    private val mapMongoClient: MapMongoClient,
    private val transitionMapperRepository: TransitionMapperRepository,
    private val achievementCacheService: AchievementCacheService,
    private val mapClient: MapClient
) {
    private val logger = LoggerFactory.getLogger(MapService::class.java)

    /**
     * Finds the closest map to a character.
     *
     * @param character The character to find the closest map to
     * @param contentType Optional filter for map content
     * @param contentCode Optional filter for map content code
     * @return A Pair of x and y coordinates of the closest map, or null if no maps are found
     */
    fun findClosestMap(
        character: ArtifactsCharacter,
        contentType: String? = null,
        contentCode: String? = null,
        checkAchievement: Boolean = false,
        excludeMapIds: Set<Int> = emptySet()
    ): MapData {
        logger.debug("Finding closest map to character ${character.name} at position (${character.x}, ${character.y})")

        // Fetch maps from the local database cache
        val mapsResponse = mapMongoClient.getMaps(
            page = 1,
            size = 100, // Fetch a reasonable number of maps to compare
            content_type = contentType.takeIf { !it.isNullOrBlank() && it != "null" },
            content_code = contentCode.takeIf { !it.isNullOrBlank() && it != "null" }
        )

        return selectClosestMap(character, mapsResponse.data, contentType, contentCode, checkAchievement, excludeMapIds)
    }

    /**
     * Variante de [findClosestMap] qui interroge l'API en direct plutôt que le cache
     * local. À privilégier pour les contenus volatils (PNJ liés à des événements),
     * dont la position stockée en base peut être périmée.
     */
    fun findClosestMapFromApi(
        character: ArtifactsCharacter,
        contentType: String? = null,
        contentCode: String? = null,
        checkAchievement: Boolean = false,
        excludeMapIds: Set<Int> = emptySet()
    ): MapData {
        logger.debug("Finding closest map (live API) to character ${character.name} at position (${character.x}, ${character.y})")

        val mapsResponse = mapClient.getMaps(
            page = 1,
            size = 100,
            content_type = contentType.takeIf { !it.isNullOrBlank() && it != "null" },
            content_code = contentCode.takeIf { !it.isNullOrBlank() && it != "null" }
        )

        return selectClosestMap(character, mapsResponse.data, contentType, contentCode, checkAchievement, excludeMapIds)
    }

    /**
     * Filtre une liste de maps (exclusions + accès par achievement) puis retourne
     * la plus proche du personnage. Logique commune au cache local et à l'API.
     */
    private fun selectClosestMap(
        character: ArtifactsCharacter,
        maps: List<MapData>,
        contentType: String?,
        contentCode: String?,
        checkAchievement: Boolean,
        excludeMapIds: Set<Int>
    ): MapData {
        if (maps.isEmpty()) {
            logger.warn("No maps found with the specified criteria")
            throw UnknownMapException(contentType, contentCode)
        }

        var filteredMaps = if (excludeMapIds.isNotEmpty()) {
            maps.filter { it.mapId !in excludeMapIds }
        } else {
            maps
        }
        if (checkAchievement) {
            filteredMaps = filteredMaps.filter { mapData ->
                mapData.access?.conditions?.all { condition ->
                    condition.operator != "achievement_unlocked" ||
                        achievementCacheService.isUnlocked(character.account, condition.code)
                } ?: true
            }
        }

        if (filteredMaps.isEmpty()) {
            logger.warn("No maps found with the specified criteria after filtering for achievements")
            throw UnknownMapException(contentType, contentCode)
        }

        val closestMap = findClosestMapToCharacter(character, filteredMaps)
        logger.debug("Closest map to character ${character.name} is at position (${closestMap.x}, ${closestMap.y})")
        return closestMap
    }

    fun findByMapId(mapId: Int): MapData? {
        return mapMongoClient.getMapById(mapId)
    }

    fun isMonsterPresentAt(mapId: Int, monsterCode: String): Boolean {
        return mapClient.getMaps(content_code = monsterCode).data.any { it.mapId == mapId }
    }

    /**
     * Finds the shortest path of transitions between two regions.
     * Uses BFS to find the path.
     *
     * @param originRegion The starting region ID
     * @param destinationRegion The target region ID
     * @return A list of TransitionMapper objects representing the path, or empty if no path found
     */
    fun findTransitionPath(originRegion: Int, destinationRegion: Int): List<TransitionMapper> {
        if (originRegion == destinationRegion) return emptyList()

        val queue: MutableList<Pair<Int, List<TransitionMapper>>> = mutableListOf(originRegion to emptyList())
        val visited = mutableSetOf(originRegion)

        while (queue.isNotEmpty()) {
            val (currentRegion, path) = queue.removeAt(0)

            val transitions = transitionMapperRepository.findBySourceMapDataRegion(currentRegion)
            for (transition in transitions) {
                val nextRegion = transition.destinationMapData.region ?: continue
                if (nextRegion == destinationRegion) {
                    return path + transition
                }
                if (nextRegion !in visited) {
                    visited.add(nextRegion)
                    queue.add(nextRegion to (path + transition))
                }
            }
        }

        return emptyList()
    }

    /**
     * Calculates the Euclidean distance between a character and a map.
     *
     * @param character The character
     * @param map The map
     * @return The distance between the character and the map
     */
    private fun calculateDistance(character: ArtifactsCharacter, map: MapData): Double {
        var malus = 0
        val dx = (character.x - map.x).toDouble()
        val dy = (character.y - map.y).toDouble()
        val originMap = mapMongoClient.getMapById(character.mapId)
        if(originMap?.region != map.region){
            malus = 5
        }
        return sqrt(dx.pow(2) + dy.pow(2))+malus
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
