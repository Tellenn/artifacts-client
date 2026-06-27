package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.RaidDocument
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface RaidRepository : MongoRepository<RaidDocument, String>
