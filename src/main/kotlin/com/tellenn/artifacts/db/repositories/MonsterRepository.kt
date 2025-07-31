package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.MonsterDocument
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MonsterRepository : MongoRepository<MonsterDocument, String> {
    // Find by code
    fun findByCode(code: String): MonsterDocument?

    // Find the weakest monster that drop a specific item
    fun findFirstByDropsCodeOrderByLevelAsc(itemId: String): MonsterDocument

    fun findFirstByLevelLessThanEqualOrderByLevelDesc(level: Int): MonsterDocument

}