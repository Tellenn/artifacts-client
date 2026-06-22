package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.RaidClient
import com.tellenn.artifacts.db.documents.RaidDocument
import com.tellenn.artifacts.db.repositories.RaidRepository
import com.tellenn.artifacts.exceptions.NotFoundException
import com.tellenn.artifacts.models.Raid
import com.tellenn.artifacts.models.RaidSchedule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

/**
 * Reads cached raid definitions, queries live raid state, and computes raid start times.
 */
@Service
class RaidService(
    private val raidRepository: RaidRepository,
    private val raidClient: RaidClient,
) {
    private val logger = LoggerFactory.getLogger(RaidService::class.java)

    fun getAllCachedRaids(): List<Raid> =
        raidRepository.findAll().map { RaidDocument.toRaid(it) }

    fun getCachedRaid(code: String): Raid? =
        raidRepository.findById(code).map { RaidDocument.toRaid(it) }.orElse(null)

    /**
     * Fetches the live state of a raid from the API by its code, including the
     * active instance when one is running. Returns null when the code is unknown.
     */
    fun getLiveRaid(code: String): Raid? =
        try {
            raidClient.getRaid(code).data
        } catch (e: NotFoundException) {
            logger.warn("Raid {} not found via API", code)
            null
        }

    /**
     * Computes the next UTC start instant strictly after [now] for the given schedule,
     * walking forward day by day over the scheduled weekdays.
     */
    fun computeNextStart(schedule: RaidSchedule, now: Instant): Instant {
        require(schedule.weekdays.isNotEmpty()) { "Raid schedule has no weekdays" }
        val days = schedule.weekdays.map { DayOfWeek.valueOf(it.uppercase()) }.toSet()
        val base = now.atZone(ZoneOffset.UTC)
        for (offset in 0..7) {
            val candidate = base.plusDays(offset.toLong())
                .withHour(schedule.startHourUtc)
                .withMinute(schedule.startMinuteUtc)
                .withSecond(0)
                .withNano(0)
            if (candidate.dayOfWeek in days && candidate.toInstant().isAfter(now)) {
                return candidate.toInstant()
            }
        }
        // Unreachable for a non-empty weekday set within an 8-day window.
        throw IllegalStateException("Could not compute next start for schedule $schedule")
    }
}
