package com.tellenn.artifacts.services

import com.tellenn.artifacts.models.Raid
import com.tellenn.artifacts.models.RaidSchedule
import com.tellenn.artifacts.utils.TimeUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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

    @Test
    fun `onRaidWindow attempts the raid and reschedules the next occurrence`() {
        `when`(raidService.getCachedRaid("god_of_the_sun")).thenReturn(raid())
        `when`(raidService.computeNextStart(raid().schedule, timeUtils.now()))
            .thenReturn(Instant.parse("2026-06-29T21:00:00Z"))

        scheduler.onRaidWindow("god_of_the_sun")

        verify(raidFightService).attemptRaid("god_of_the_sun")
        // reschedule path recomputes the next start
        verify(raidService).computeNextStart(raid().schedule, timeUtils.now())
    }

    @Test
    fun `onRaidWindow still reschedules when the raid attempt throws`() {
        `when`(raidService.getCachedRaid("god_of_the_sun")).thenReturn(raid())
        `when`(raidService.computeNextStart(raid().schedule, timeUtils.now()))
            .thenReturn(Instant.parse("2026-06-29T21:00:00Z"))
        `when`(raidFightService.attemptRaid("god_of_the_sun"))
            .thenThrow(RuntimeException("boom"))

        // must not propagate — the scheduler thread survives a failing attempt
        scheduler.onRaidWindow("god_of_the_sun")

        // finally-block reschedule ran despite the exception
        verify(raidService).computeNextStart(raid().schedule, timeUtils.now())
    }
}
