package com.tellenn.artifacts.services

import com.tellenn.artifacts.utils.TimeUtils
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Schedules, for each cached raid, a check fired LEAD_TIME_MINUTES before the raid's
 * next start. Each check delegates to RaidFightService and reschedules itself for the
 * following occurrence.
 */
@Component
class RaidScheduler(
    private val raidService: RaidService,
    private val raidFightService: RaidFightService,
    private val timeUtils: TimeUtils,
) {
    private val logger = LoggerFactory.getLogger(RaidScheduler::class.java)

    // Pool size 2: raids are mutually exclusive on the shared party, but a long-running
    // raid fight must not starve scheduling of the next raid's window.
    private val scheduler = Executors.newScheduledThreadPool(2)

    companion object {
        const val LEAD_TIME_MINUTES = 2L
    }

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        val raids = raidService.getAllCachedRaids()
        logger.info("Scheduling {} raids", raids.size)
        raids.forEach { scheduleNext(it.code) }
    }

    private fun scheduleNext(raidCode: String) {
        val raid = raidService.getCachedRaid(raidCode) ?: run {
            logger.warn("Raid {} not found in cache, will not reschedule", raidCode)
            return
        }
        val nextStart = raidService.computeNextStart(raid.schedule, timeUtils.now())
        val fireAt = nextStart.minus(Duration.ofMinutes(LEAD_TIME_MINUTES))
        val delayMs = maxOf(0L, Duration.between(timeUtils.now(), fireAt).toMillis())
        scheduler.schedule({ onRaidWindow(raidCode) }, delayMs, TimeUnit.MILLISECONDS)
        logger.info("Raid {} scheduled in {} ms (next start {})", raidCode, delayMs, nextStart)
    }

    /** Runs one raid window: attempt the raid, then reschedule the following occurrence. */
    fun onRaidWindow(raidCode: String) {
        try {
            raidFightService.attemptRaid(raidCode)
        } catch (e: Exception) {
            logger.error("Raid window failed for {}", raidCode, e)
        } finally {
            scheduleNext(raidCode)
        }
    }

    @PreDestroy
    fun shutdown() {
        scheduler.shutdownNow()
    }
}
