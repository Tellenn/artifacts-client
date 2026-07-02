package com.tellenn.artifacts.services

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MissionMetricsTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var missionMetrics: MissionMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        missionMetrics = MissionMetrics(meterRegistry)
    }

    @Test
    fun `should record mission duration with success outcome`() {
        // when
        val result = missionMetrics.timeMission("Cloud", "AUTOMATIC") { 42 }

        // then
        val timer = meterRegistry.get("artifacts.mission.duration")
            .tag("character", "Cloud")
            .tag("priority", "AUTOMATIC")
            .tag("outcome", "success")
            .timer()
        assertEquals(1, timer.count())
        assertEquals(42, result)
    }

    @Test
    fun `should record mission duration with error outcome and rethrow`() {
        // when
        assertThrows(IllegalStateException::class.java) {
            missionMetrics.timeMission("Renoir", "HUMAN_ORDER") { error("boom") }
        }

        // then
        val timer = meterRegistry.get("artifacts.mission.duration")
            .tag("character", "Renoir")
            .tag("priority", "HUMAN_ORDER")
            .tag("outcome", "error")
            .timer()
        assertEquals(1, timer.count())
    }

    @Test
    fun `should expose thread alive state as gauge per character`() {
        // given
        var alive = true

        // when
        missionMetrics.registerCharacter("Kepo", threadAlive = { alive }, onMission = { false })

        // then
        val gauge = meterRegistry.get("artifacts.character.thread.alive")
            .tag("character", "Kepo")
            .gauge()
        assertEquals(1.0, gauge.value())

        // when the thread dies
        alive = false

        // then
        assertEquals(0.0, gauge.value())
    }

    @Test
    fun `should expose on mission state as gauge per character`() {
        // given
        var onMission = true

        // when
        missionMetrics.registerCharacter("Aerith", threadAlive = { true }, onMission = { onMission })

        // then
        val gauge = meterRegistry.get("artifacts.character.on.mission")
            .tag("character", "Aerith")
            .gauge()
        assertEquals(1.0, gauge.value())

        // when the mission ends
        onMission = false

        // then
        assertEquals(0.0, gauge.value())
    }

    @Test
    fun `should keep a single gauge when the same character is registered twice`() {
        // when
        missionMetrics.registerCharacter("Renoir", threadAlive = { true }, onMission = { false })
        missionMetrics.registerCharacter("Renoir", threadAlive = { true }, onMission = { false })

        // then
        val gauges = meterRegistry.get("artifacts.character.thread.alive")
            .tag("character", "Renoir")
            .gauges()
        assertEquals(1, gauges.size)
    }
}
