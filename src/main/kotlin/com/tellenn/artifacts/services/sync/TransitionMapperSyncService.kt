package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.db.repositories.MapRepository
import com.tellenn.artifacts.db.repositories.TransitionMapperRepository
import com.tellenn.artifacts.models.Conditions
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.models.TransitionMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service to manage syncing and inputting TransitionMapper data.
 */
@Service
class TransitionMapperSyncService(
    private val transitionMapperRepository: TransitionMapperRepository,
    private val mapRepository: MapRepository
) {
    private val logger = LoggerFactory.getLogger(TransitionMapperSyncService::class.java)

    /**
     * Clears existing transition mappers and saves a new set of data.
     * This fulfills the requirement to "input my own table".
     *
     * @param mappers The list of TransitionMapper objects to save
     * @return The number of mappers saved
     */
    @Transactional
    fun syncTransitionMappers(): Int {
        // Deleting existing mappers to "input our own table" fresh
        transitionMapperRepository.deleteAll()

        val savedMappers = transitionMapperRepository.saveAll(parseMapDataForTransitions(mapRepository.findAll()))
        logger.info("Successfully saved ${savedMappers.size} transition mappers")
        
        return savedMappers.size
    }

    /**
     * Returns all transition mappers.
     */
    fun getAllMappers(): List<TransitionMapper> {
        return transitionMapperRepository.findAll()
    }

    /**
     * Parses MapData objects and creates TransitionMapper objects for each transition found.
     *
     * @param maps The list of MapData to parse
     * @return The list of created TransitionMapper objects
     */
    fun parseMapDataForTransitions(maps: List<MapData>): List<TransitionMapper> {
        val transitionMappers = mutableListOf<TransitionMapper>()

        maps.forEach { mapData ->
            val transition = mapData.interactions?.transition
            if (transition != null) {
                val targetMap = mapRepository.findByMapId(transition.mapId)
                if (targetMap != null) {
                    val transitionMapper = TransitionMapper(
                        sourceMapData = mapData,
                        destinationMapData = targetMap,
                        conditions = transition.conditions
                    )
                    transitionMappers.add(transitionMapper)
                }
            }
        }

        return transitionMappers
    }

    fun manualMappers(): List<TransitionMapper>{
        return listOf(
            TransitionMapper(
                id="1",
                sourceMapData=mapRepository.findByMapId(571)!!,
                destinationMapData=mapRepository.findByMapId(572)!!,
                conditions = listOf()),
            TransitionMapper(
                id="2",
                sourceMapData=mapRepository.findByMapId(572)!!,
                destinationMapData=mapRepository.findByMapId(571)!!,
                conditions = listOf()),
            TransitionMapper(
                id="3",
                sourceMapData=mapRepository.findByMapId(133)!!,
                destinationMapData=mapRepository.findByMapId(134)!!,
                conditions = listOf()),
            TransitionMapper(
                id="4",
                sourceMapData=mapRepository.findByMapId(134)!!,
                destinationMapData=mapRepository.findByMapId(133)!!,
                conditions = listOf()),
            TransitionMapper(
                id="5",
                sourceMapData=mapRepository.findByMapId(77)!!,
                destinationMapData=mapRepository.findByMapId(71)!!,
                conditions = listOf(Conditions("priestess_hideout_key", "cost", 1))),
            TransitionMapper(
                id="6",
                sourceMapData=mapRepository.findByMapId(71)!!,
                destinationMapData=mapRepository.findByMapId(77)!!,
                conditions = listOf()),
            TransitionMapper(
                id="7",
                sourceMapData=mapRepository.findByMapId(655)!!,
                destinationMapData=mapRepository.findByMapId(656)!!,
                conditions = listOf(Conditions("lich_tomb_key", "cost", 1))),
            TransitionMapper(
                id="8",
                sourceMapData=mapRepository.findByMapId(656)!!,
                destinationMapData=mapRepository.findByMapId(655)!!,
                conditions = listOf()),
            TransitionMapper(
                id="9",
                sourceMapData=mapRepository.findByMapId(934)!!,
                destinationMapData=mapRepository.findByMapId(935)!!,
                conditions = listOf(Conditions("cultist_cloak", "has_item", 1))),
            TransitionMapper(
                id="10",
                sourceMapData=mapRepository.findByMapId(935)!!,
                destinationMapData=mapRepository.findByMapId(934)!!,
                conditions = listOf(Conditions("cultist_cloak", "has_item", 1)))
        )
    }
}
