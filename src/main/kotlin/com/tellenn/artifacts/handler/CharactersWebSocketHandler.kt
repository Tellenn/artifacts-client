package com.tellenn.artifacts.handler

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.CopyOnWriteArraySet

@Component
class CharactersWebSocketHandler : TextWebSocketHandler() {

    private val sessions = CopyOnWriteArraySet<WebSocketSession>()
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
        logger.info("WebSocket connected: ${session.id}")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        logger.info("Received message from ${session.id}: ${message.payload}")

    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
        logger.info("WebSocket disconnected: ${session.id}")
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error("WebSocket error for session ${session.id}", exception)
    }

    fun sendMessageToAll(message: String) {
        sessions.forEach { session ->
            if (session.isOpen) {
                try {
                    session.sendMessage(TextMessage(message))
                } catch (e: Exception) {
                    // Handle or log error sending to this session
                }
            }
        }
    }
}
