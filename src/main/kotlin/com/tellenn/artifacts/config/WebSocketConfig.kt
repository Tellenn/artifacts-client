package com.tellenn.artifacts.config

import com.tellenn.artifacts.handler.CharactersWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry


@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val charactersSocketHandler: CharactersWebSocketHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(charactersSocketHandler, "/ws/characters")
            .setAllowedOriginPatterns("*")
    }
}