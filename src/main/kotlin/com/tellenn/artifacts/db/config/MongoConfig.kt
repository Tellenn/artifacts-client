package com.tellenn.artifacts.db.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@Configuration
@EnableMongoRepositories(basePackages = ["com.tellenn.artifacts.db.repositories"])
class MongoConfig