package com.tellenn.artifacts.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test

@Testcontainers
@Configuration
class MongoTestConfiguration {

    companion object {
        @Container
        val mongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:8.0")).apply {
            start()
        }
    }

    @Bean
    @Primary
    fun mongoTemplate(): MongoTemplate {
        val connectionString = mongoDBContainer.replicaSetUrl
        println("I setup the uri here : $connectionString")
        val factory = SimpleMongoClientDatabaseFactory(connectionString)
        return MongoTemplate(factory)
    }

    @Test
    fun contextLoads() {
        // Test simple pour vérifier que le contexte se charge
    }
}
