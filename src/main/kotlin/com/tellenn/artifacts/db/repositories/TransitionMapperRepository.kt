package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.models.TransitionMapper
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface TransitionMapperRepository : MongoRepository<TransitionMapper, String> {

    fun findBySourceMapDataRegion(sourceRegion: Int?): List<TransitionMapper>
}
