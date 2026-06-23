package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.RaidClient
import com.tellenn.artifacts.db.repositories.RaidRepository
import com.tellenn.artifacts.models.Raid
import com.tellenn.artifacts.models.RaidSchedule
import com.tellenn.artifacts.utils.TimeUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Duration
import java.time.Instant

class RaidSchedulerTest {

    private lateinit var raidService: RaidService
    private lateinit var raidFightService: RaidFightService
    private lateinit var timeUtils: TimeUtils
    private lateinit var scheduler: RaidScheduler

    @BeforeEach
    fun setUp() {
        raidService = mock(RaidService::class.java)
        raidFightService = mock(RaidFightService::class.java)
        timeUtils = mock(TimeUtils::class.java)
        `when`(timeUtils.now()).thenReturn(Instant.parse("2026-06-22T20:58:00Z"))
        scheduler = RaidScheduler(raidService, raidFightService, timeUtils)
    }

    @AfterEach
    fun tearDown() = scheduler.shutdown()

    private fun raid() = Raid(
        code = "god_of_the_sun", name = "God of the Sun", description = null,
        monster = "sonnengott", schedule = RaidSchedule(listOf("monday"), 21, 0, 12), rewards = null,
    )

    // The reschedule references the next start from now + lead time (see RaidScheduler.nextDelayMs).
    private val leadAdjustedNow: Instant = Instant.parse("2026-06-22T20:58:00Z")
        .plus(Duration.ofMinutes(RaidScheduler.LEAD_TIME_MINUTES))

    @Test
    fun `onRaidWindow attempts the raid and reschedules the next occurrence`() {
        `when`(raidService.getCachedRaid("god_of_the_sun")).thenReturn(raid())
        `when`(raidService.computeNextStart(raid().schedule, leadAdjustedNow))
            .thenReturn(Instant.parse("2026-06-29T21:00:00Z"))

        scheduler.onRaidWindow("god_of_the_sun")

        verify(raidFightService).attemptRaid("god_of_the_sun")
        // reschedule path recomputes the next start
        verify(raidService).computeNextStart(raid().schedule, leadAdjustedNow)
    }

    @Test
    fun `reschedule skips the current occurrence instead of refiring inside the lead window`() {
        // Real computeNextStart: the bug only shows with the actual day-walk logic.
        val realRaidService = RaidService(mock(RaidRepository::class.java), mock(RaidClient::class.java))
        val leadScheduler = RaidScheduler(realRaidService, raidFightService, timeUtils)
        // Monday 2026-06-22T20:58:00Z — exactly LEAD_TIME_MINUTES before the 21:00 start,
        // i.e. the instant onRaidWindow fires and immediately reschedules.
        val firedAt = Instant.parse("2026-06-22T20:58:00Z")

        val delayMs = leadScheduler.nextDelayMs(raid(), firedAt)
        leadScheduler.shutdown()

        // Must wait ~a week for next Monday, not refire near-immediately for the
        // occurrence we just handled (which would hammer the API for the full lead window).
        assertTrue(
            delayMs > Duration.ofDays(6).toMillis(),
            "expected a next-week delay, but got $delayMs ms (immediate refire bug)",
        )
    }

    @Test
    fun `onRaidWindow still reschedules when the raid attempt throws`() {
        `when`(raidService.getCachedRaid("god_of_the_sun")).thenReturn(raid())
        `when`(raidService.computeNextStart(raid().schedule, leadAdjustedNow))
            .thenReturn(Instant.parse("2026-06-29T21:00:00Z"))
        `when`(raidFightService.attemptRaid("god_of_the_sun"))
            .thenThrow(RuntimeException("boom"))

        // must not propagate — the scheduler thread survives a failing attempt
        scheduler.onRaidWindow("god_of_the_sun")

        // finally-block reschedule ran despite the exception
        verify(raidService).computeNextStart(raid().schedule, leadAdjustedNow)
    }
}
