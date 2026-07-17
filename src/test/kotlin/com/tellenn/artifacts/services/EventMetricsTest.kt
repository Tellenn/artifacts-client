package com.tellenn.artifacts.services

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventMetricsTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var eventMetrics: EventMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        eventMetrics = EventMetrics(meterRegistry)
    }

    @Test
    fun `records an event tagged by type and code`() {
        // when
        eventMetrics.recordEvent("event_spawn", "strange_apparition")

        // then
        val counter = meterRegistry.get("artifacts.event.received")
            .tag("type", "event_spawn")
            .tag("code", "strange_apparition")
            .counter()
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `keeps separate counters per type`() {
        // when
        eventMetrics.recordEvent("event_spawn", "strange_apparition")
        eventMetrics.recordEvent("event_spawn", "strange_apparition")
        eventMetrics.recordEvent("achievement_unlocked", "-")

        // then
        val spawns = meterRegistry.get("artifacts.event.received")
            .tag("type", "event_spawn").counter()
        val achievements = meterRegistry.get("artifacts.event.received")
            .tag("type", "achievement_unlocked").counter()
        assertEquals(2.0, spawns.count())
        assertEquals(1.0, achievements.count())
    }
}
