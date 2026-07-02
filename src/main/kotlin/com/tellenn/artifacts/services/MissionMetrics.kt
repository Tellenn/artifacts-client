package com.tellenn.artifacts.services

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Exposes mission processing time and character thread health to the metrics registry.
 */
@Component
class MissionMetrics(private val meterRegistry: MeterRegistry) {

    companion object {
        private const val MISSION_TIMER_NAME = "artifacts.mission.duration"
        private const val THREAD_ALIVE_GAUGE_NAME = "artifacts.character.thread.alive"
        private const val ON_MISSION_GAUGE_NAME = "artifacts.character.on.mission"
    }

    /** Strong references to the state probes — Micrometer only keeps weak ones. */
    private val registeredProbes = ConcurrentHashMap<String, CharacterProbes>()

    private class CharacterProbes(val threadAlive: () -> Boolean, val onMission: () -> Boolean)

    fun <T> timeMission(character: String, priority: String, mission: () -> T): T {
        val sample = Timer.start(meterRegistry)
        return try {
            mission().also { sample.stop(missionTimer(character, priority, "success")) }
        } catch (e: Exception) {
            sample.stop(missionTimer(character, priority, "error"))
            throw e
        }
    }

    /**
     * Registers live health gauges for a character. Idempotent — re-registering on a
     * thread restart keeps the original gauges.
     */
    fun registerCharacter(character: String, threadAlive: () -> Boolean, onMission: () -> Boolean) {
        registeredProbes.computeIfAbsent(character) { name ->
            CharacterProbes(threadAlive, onMission).also { probes ->
                registerBooleanGauge(THREAD_ALIVE_GAUGE_NAME, name, probes.threadAlive)
                registerBooleanGauge(ON_MISSION_GAUGE_NAME, name, probes.onMission)
            }
        }
    }

    private fun registerBooleanGauge(gaugeName: String, character: String, probe: () -> Boolean) {
        Gauge.builder(gaugeName, probe) { if (it()) 1.0 else 0.0 }
            .tag("character", character)
            .register(meterRegistry)
    }

    private fun missionTimer(character: String, priority: String, outcome: String): Timer =
        Timer.builder(MISSION_TIMER_NAME)
            .description("Processing time of character missions")
            .tag("character", character)
            .tag("priority", priority)
            .tag("outcome", outcome)
            .publishPercentileHistogram()
            .register(meterRegistry)
}
