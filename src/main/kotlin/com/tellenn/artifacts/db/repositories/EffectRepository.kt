package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.EffectDocument
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface EffectRepository : MongoRepository<EffectDocument, String>
