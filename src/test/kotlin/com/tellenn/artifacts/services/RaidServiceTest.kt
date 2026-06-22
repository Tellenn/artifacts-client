package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.RaidClient
import com.tellenn.artifacts.db.repositories.RaidRepository
import com.tellenn.artifacts.models.RaidSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.time.Instant

class RaidServiceTest {

    private lateinit var raidRepository: RaidRepository
    private lateinit var raidClient: RaidClient
    private lateinit var service: RaidService

    @BeforeEach
    fun setUp() {
        raidRepository = mock(RaidRepository::class.java)
        raidClient = mock(RaidClient::class.java)
        service = RaidService(raidRepository, raidClient)
    }

    private fun schedule(vararg days: String, hour: Int = 21, minute: Int = 0) =
        RaidSchedule(days.toList(), hour, minute, 12)

    @Test
    fun `computeNextStart returns today's slot when it is still in the future`() {
        // Monday 2026-06-22T10:00:00Z, slot at 21:00 same day
        val now = Instant.parse("2026-06-22T10:00:00Z")
        val next = service.computeNextStart(schedule("monday"), now)
        assertEquals(Instant.parse("2026-06-22T21:00:00Z"), next)
    }

    @Test
    fun `computeNextStart skips to next matching weekday when today's slot has passed`() {
        // Monday 2026-06-22T22:00:00Z (after 21:00) -> next Monday a week later
        val now = Instant.parse("2026-06-22T22:00:00Z")
        val next = service.computeNextStart(schedule("monday"), now)
        assertEquals(Instant.parse("2026-06-29T21:00:00Z"), next)
    }

    @Test
    fun `computeNextStart picks the soonest of several weekdays`() {
        // Monday 2026-06-22T22:00:00Z, schedule Mon/Wed/Fri -> Wednesday 2026-06-24
        val now = Instant.parse("2026-06-22T22:00:00Z")
        val next = service.computeNextStart(schedule("monday", "wednesday", "friday"), now)
        assertEquals(Instant.parse("2026-06-24T21:00:00Z"), next)
    }

    @Test
    fun `computeNextStart wraps across the week boundary`() {
        // Saturday 2026-06-27T22:00:00Z, schedule only Monday -> Monday 2026-06-29
        val now = Instant.parse("2026-06-27T22:00:00Z")
        val next = service.computeNextStart(schedule("monday"), now)
        assertEquals(Instant.parse("2026-06-29T21:00:00Z"), next)
    }

    @Test
    fun `computeNextStart honours the start minute`() {
        val now = Instant.parse("2026-06-22T10:00:00Z")
        val next = service.computeNextStart(schedule("monday", hour = 21, minute = 30), now)
        assertEquals(Instant.parse("2026-06-22T21:30:00Z"), next)
    }

    @Test
    fun `computeNextStart rejects an empty weekday list`() {
        val now = Instant.parse("2026-06-22T10:00:00Z")
        assertThrows<IllegalArgumentException> { service.computeNextStart(schedule(), now) }
    }
}
