package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.models.Achievement
import com.tellenn.artifacts.models.Rewards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class AchievementCacheServiceTest {

    /** Horloge pilotable pour tester l'expiration du TTL sans attendre. */
    private class MutableClock(var current: Instant) : Clock() {
        override fun instant(): Instant = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    private val accountClient = mock(AccountClient::class.java)
    private val clock = MutableClock(Instant.parse("2026-07-14T10:00:00Z"))
    private val service = AchievementCacheService(accountClient, clock)

    @Test
    fun `deux lectures dans la fenetre TTL ne declenchent qu'un seul appel API`() {
        // given
        `when`(accountClient.getAccountAchievements("Tellenn", true, 1))
            .thenReturn(response(listOf(achievement("first_kill"))))

        // when
        service.getCompletedAchievements("Tellenn")
        service.getCompletedAchievements("Tellenn")

        // then — le quota horaire du compte n'est consommé qu'une fois
        verify(accountClient, times(1)).getAccountAchievements("Tellenn", true, 1)
    }

    @Test
    fun `une lecture apres expiration du TTL rafraichit depuis l'API`() {
        // given
        `when`(accountClient.getAccountAchievements("Tellenn", true, 1))
            .thenReturn(response(listOf(achievement("first_kill"))))
        service.getCompletedAchievements("Tellenn")

        // when — on dépasse la fenêtre de cache
        clock.current = clock.current.plus(Duration.ofMinutes(6))
        service.getCompletedAchievements("Tellenn")

        // then
        verify(accountClient, times(2)).getAccountAchievements("Tellenn", true, 1)
    }

    @Test
    fun `toutes les pages d'achievements sont agregees`() {
        // given — 2 pages côté API : les appelants ne doivent plus être limités à la page 1
        `when`(accountClient.getAccountAchievements("Tellenn", true, 1))
            .thenReturn(response(listOf(achievement("first_kill")), page = 1, pages = 2))
        `when`(accountClient.getAccountAchievements("Tellenn", true, 2))
            .thenReturn(response(listOf(achievement("draconic_harvest")), page = 2, pages = 2))

        // when
        val achievements = service.getCompletedAchievements("Tellenn")

        // then
        assertEquals(listOf("first_kill", "draconic_harvest"), achievements.map { it.code })
    }

    @Test
    fun `isUnlocked reflete la presence du code dans les achievements completes`() {
        // given
        `when`(accountClient.getAccountAchievements("Tellenn", true, 1))
            .thenReturn(response(listOf(achievement("draconic_harvest"))))

        // when / then
        assertTrue(service.isUnlocked("Tellenn", "draconic_harvest"))
        assertFalse(service.isUnlocked("Tellenn", "unknown_achievement"))
    }

    private fun achievement(code: String) = Achievement(
        name = "Achievement $code",
        code = code,
        description = "Desc",
        points = 10,
        objectives = emptyList(),
        rewards = Rewards(0, null),
        completedAt = "now",
    )

    private fun response(
        achievements: List<Achievement>,
        page: Int = 1,
        pages: Int = 1,
    ) = ArtifactsArrayResponseBody(
        data = achievements,
        total = achievements.size,
        page = page,
        size = 50,
        pages = pages,
    )
}
