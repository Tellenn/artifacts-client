package com.tellenn.artifacts.config

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for MongoDB integration tests using TestContainers.
 * This class sets up a MongoDB container for testing and configures Spring to use it.
 * Extend this class in your test classes to have access to a real MongoDB instance for testing.
 */
@SpringBootTest
@Import(MongoTestConfiguration::class)
@Testcontainers
abstract class MongoTestBase