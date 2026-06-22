// src/test/kotlin/com/tellenn/artifacts/models/RaidModelDeserializationTest.kt
package com.tellenn.artifacts.models

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RaidModelDeserializationTest {

    private val objectMapper = jacksonObjectMapper().apply { registerModule(JavaTimeModule()) }

    private val sample = """
    {
      "data": [
        {
          "code": "god_of_the_sun",
          "name": "God of the Sun",
          "description": "A powerful fire elemental.",
          "monster": "sonnengott",
          "schedule": {
            "weekdays": ["monday", "wednesday", "friday"],
            "start_hour_utc": 21,
            "start_minute_utc": 0,
            "duration_hours": 12
          },
          "rewards": {
            "damage_rewards": [
              { "damage_per_reward": 20000, "max_rewards": 25, "items": [ { "code": "sonnengott_coin", "quantity": 1 } ] }
            ],
            "leaderboard": [
              { "min_rank": 1, "max_rank": 1, "items": [ { "code": "sonnengott_coin", "quantity": 3 } ] }
            ]
          },
          "status": "finished_failure",
          "next_start_at": "2026-06-22T21:00:00Z",
          "participant_count": 0,
          "active_instance": null,
          "latest_instance": {
            "starts_at": "2026-06-19T21:00:00Z",
            "ends_at": "2026-06-20T09:00:00Z",
            "status": "finished_failure",
            "total_hp": 500000,
            "remaining_hp": 500000,
            "participant_count": 0,
            "ended_at": "2026-06-20T09:00:00",
            "result": "failure",
            "rewards_distributed_at": "2026-06-20T18:38:39.498000"
          }
        }
      ],
      "total": 1, "page": 1, "size": 50, "pages": 1
    }
    """.trimIndent()

    @Test
    fun `parses raids list including snake_case and zoneless timestamps`() {
        val response = objectMapper.readValue<ArtifactsArrayResponseBody<Raid>>(sample)
        val raid = response.data.single()

        assertEquals("god_of_the_sun", raid.code)
        assertEquals("sonnengott", raid.monster)
        assertEquals(listOf("monday", "wednesday", "friday"), raid.schedule.weekdays)
        assertEquals(21, raid.schedule.startHourUtc)
        assertEquals(12, raid.schedule.durationHours)
        assertEquals(500000L, raid.latestInstance?.totalHp)
        assertEquals("2026-06-20T09:00:00", raid.latestInstance?.endedAt) // no 'Z' must not break parsing
        assertTrue(raid.latestInstance!!.isFinished())
        assertEquals("sonnengott_coin", raid.rewards?.damageRewards?.first()?.items?.first()?.code)
    }

    @Test
    fun `isFinished is false for an active status`() {
        val instance = RaidInstance(
            startsAt = null, endsAt = null, status = "active",
            totalHp = 100, remainingHp = 50, participantCount = 1,
            endedAt = null, result = null, rewardsDistributedAt = null,
        )
        assertFalse(instance.isFinished())
    }
}
