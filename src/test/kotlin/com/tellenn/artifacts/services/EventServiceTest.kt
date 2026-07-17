package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.EventClient
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.Content
import com.tellenn.artifacts.models.EventData
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Reconnaissance des monstres d'événement : leurs drops ne sont collectables que quand
 * l'événement est actif — le CrafterJob doit pouvoir les distinguer des monstres permanents.
 */
class EventServiceTest {

    private lateinit var eventClient: EventClient
    private lateinit var eventService: EventService

    @BeforeEach
    fun setUp() {
        eventClient = mock(EventClient::class.java)
        eventService = EventService(
            itemRepository = mock(ItemRepository::class.java),
            monsterService = mock(MonsterService::class.java),
            eventClient = eventClient,
        )
        `when`(eventClient.getEvents("monster", 1, 50)).thenReturn(
            ArtifactsArrayResponseBody(
                data = listOf(monsterEvent("full_moon_vampire"), monsterEvent("bandit_lizard")),
                total = 2, page = 1, size = 50, pages = 1,
            )
        )
    }

    @Test
    fun `isEventMonster reconnait un monstre d'evenement`() {
        assertTrue(eventService.isEventMonster("full_moon_vampire"))
    }

    @Test
    fun `isEventMonster renvoie false pour un monstre permanent`() {
        assertFalse(eventService.isEventMonster("chicken"))
    }

    @Test
    fun `isEventMonster ne recharge pas les definitions d'evenements a chaque appel`() {
        // Les définitions d'événements sont statiques pour la saison : une seule requête suffit,
        // le garde-fou de faisabilité est appelé à chaque recette du CrafterJob.
        eventService.isEventMonster("chicken")
        eventService.isEventMonster("full_moon_vampire")
        verify(eventClient, times(1)).getEvents("monster", 1, 50)
    }

    private fun monsterEvent(code: String) = EventData(
        name = code, code = code, content = Content("monster", code), maps = emptyList(),
        duration = "60", rate = 1, cooldown = 0, price = null, transition = null,
        cooldownExpiration = null,
    )
}
