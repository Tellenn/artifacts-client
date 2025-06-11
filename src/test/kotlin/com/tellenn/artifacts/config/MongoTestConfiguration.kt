package com.tellenn.artifacts.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import jakarta.annotation.PostConstruct

@TestConfiguration
class MongoTestConfiguration : ApplicationContextInitializer<ConfigurableApplicationContext> {

    companion object {
        val mongoDBContainer: MongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:6.0"))
            .apply { start() }
    }

    override fun initialize(context: ConfigurableApplicationContext) {
        TestPropertyValues.of(
            "spring.data.mongodb.uri=${mongoDBContainer.connectionString}",
            "spring.data.mongodb.database=test"
        ).applyTo(context.environment)
    }

    @Bean
    @Primary
    fun mongoClient(): MongoClient {
        return MongoClients.create(mongoDBContainer.connectionString)
    }

    @Bean
    @Primary
    fun mongoTemplate(mongoClient: MongoClient): MongoTemplate {
        return MongoTemplate(SimpleMongoClientDatabaseFactory(mongoClient, "test"))
    }
}