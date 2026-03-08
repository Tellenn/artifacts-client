package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.models.TransitionMapper
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface TransitionMapperRepository : MongoRepository<TransitionMapper, String> {
    @Query("{ 'sourceMapData.region': ?0, 'destinationMapData.region': ?1 }")
    fun findBySourceMapDataRegionAndDestinationMapDataRegion(sourceRegion: Int, destinationRegion: Int): TransitionMapper?

    fun findByDestinationMapDataRegion(destinationRegion: Int?): List<TransitionMapper>
}
