package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.EventClient
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for working with resources.
 * Provides methods to find resources based on character skills and levels.
 */
@Service
class EventService(
    private val itemRepository: ItemRepository,
    private val monsterService: MonsterService,
    private val eventClient: EventClient
) {
    private val logger = LoggerFactory.getLogger(EventService::class.java)

    fun getAllEventMaterials() : List<String>{
        val drops : MutableList<String> = mutableListOf()
        eventClient.getEvents(type = "monster")
            .data
            .map { it.content.code }
            .map { monsterService.findMonster(it) }
            .forEach { it.drops?.forEach { drops.add(it.code) } }
        return drops.toList()
    }

}
