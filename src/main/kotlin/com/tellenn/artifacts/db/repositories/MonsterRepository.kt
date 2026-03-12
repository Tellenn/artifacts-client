package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.models.MonsterData
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface MonsterRepository : MongoRepository<MonsterData, String> {
    // Find by code
    fun findByCode(code: String): MonsterData

    // Find the weakest monster that drop a specific item
    fun findFirstByDropsCodeOrderByLevelAsc(itemId: String): MonsterData?

    fun findFirstByLevelLessThanEqualOrderByLevelDesc(level: Int): MonsterData

}