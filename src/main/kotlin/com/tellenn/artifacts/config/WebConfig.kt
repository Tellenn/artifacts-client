package com.tellenn.artifacts.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
/*
@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {

        registry.addMapping("/**")
            .allowedOrigins("http://localhost:8081") // Specify the allowed origins
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Specify the allowed methods
            .allowedHeaders("Content-Type", "Authorization") // Specify the allowed headers
            .allowCredentials(true) // Allow credentials if needed
    }
}*/