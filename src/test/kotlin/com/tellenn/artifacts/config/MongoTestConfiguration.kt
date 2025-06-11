package com.tellenn.artifacts.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

@TestConfiguration
@EnableMongoRepositories(basePackages = ["com.tellenn.artifacts.db.repositories"])
class MongoTestConfiguration {

    companion object {
        @Container
        val mongoDBContainer: MongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:6.0"))
            .apply { start() }
    }

    @Bean
    fun mongoClient(): MongoClient {
        return MongoClients.create(mongoDBContainer.connectionString)
    }

    @Bean
    fun mongoTemplate(mongoClient: MongoClient): MongoTemplate {
        return MongoTemplate(SimpleMongoClientDatabaseFactory(mongoClient, "test"))
    }
}