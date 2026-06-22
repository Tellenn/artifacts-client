package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.models.Raid
import com.tellenn.artifacts.models.RaidSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RaidDocumentTest {

    private fun sampleRaid() = Raid(
        code = "god_of_the_sun",
        name = "God of the Sun",
        description = "desc",
        monster = "sonnengott",
        schedule = RaidSchedule(listOf("monday", "friday"), 21, 0, 12),
        rewards = null,
        status = "upcoming",
        nextStartAt = "2026-06-22T21:00:00Z",
    )

    @Test
    fun `fromRaid keeps static fields and toRaid round-trips them`() {
        val doc = RaidDocument.fromRaid(sampleRaid())

        assertEquals("god_of_the_sun", doc.code)
        assertEquals("sonnengott", doc.monster)
        assertEquals(listOf("monday", "friday"), doc.schedule.weekdays)
        assertEquals(21, doc.schedule.startHourUtc)

        val raid = RaidDocument.toRaid(doc)
        assertEquals("god_of_the_sun", raid.code)
        assertEquals("sonnengott", raid.monster)
        assertEquals(12, raid.schedule.durationHours)
        // live-only fields are not persisted
        assertNull(raid.status)
        assertNull(raid.nextStartAt)
        assertNull(raid.activeInstance)
    }
}
