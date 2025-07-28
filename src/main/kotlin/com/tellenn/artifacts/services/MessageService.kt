package com.tellenn.artifacts.services

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class MessageService(private val messagingTemplate: SimpMessagingTemplate) {

    fun sendCharacterMessage(message: String) {
        messagingTemplate.convertAndSend("/topic/characters", message)
    }
}
