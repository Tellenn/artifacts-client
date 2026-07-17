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

    // Les définitions d'événements sont statiques pour la saison : une seule requête suffit.
    // La présence effective du monstre sur la carte reste à vérifier en live via /maps.
    private val eventMonsterCodes: Set<String> by lazy {
        eventClient.getEvents(type = "monster")
            .data
            .map { it.content.code }
            .toSet()
    }

    fun isEventMonster(monsterCode: String): Boolean = monsterCode in eventMonsterCodes

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
