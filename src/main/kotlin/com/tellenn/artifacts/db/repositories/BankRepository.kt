package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.BankDocument
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface BankRepository : MongoRepository<BankDocument, String>