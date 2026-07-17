package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.responses.Fight
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CombatMetricsTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var combatMetrics: CombatMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        combatMetrics = CombatMetrics(meterRegistry)
    }

    private fun fight(monster: String, result: String): Fight =
        Fight(turns = 3, logs = emptyList(), result = result, opponentCode = monster, characters = emptyList())

    @Test
    fun `records a won fight tagged by character, monster and outcome`() {
        // when
        combatMetrics.recordFight("Cloud", fight(monster = "chicken", result = "win"))

        // then
        val counter = meterRegistry.get("artifacts.combat.result")
            .tag("character", "Cloud")
            .tag("monster", "chicken")
            .tag("outcome", "win")
            .counter()
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `separates wins from losses on the outcome tag`() {
        // when
        combatMetrics.recordFight("Cloud", fight(monster = "chicken", result = "win"))
        combatMetrics.recordFight("Cloud", fight(monster = "chicken", result = "win"))
        combatMetrics.recordFight("Cloud", fight(monster = "chicken", result = "loss"))

        // then
        val wins = meterRegistry.get("artifacts.combat.result")
            .tag("outcome", "win").tag("monster", "chicken").counter()
        val losses = meterRegistry.get("artifacts.combat.result")
            .tag("outcome", "loss").tag("monster", "chicken").counter()
        assertEquals(2.0, wins.count())
        assertEquals(1.0, losses.count())
    }

    @Test
    fun `does not count a response without a fight`() {
        // when
        combatMetrics.recordFight("Cloud", null)

        // then
        assertTrue(meterRegistry.find("artifacts.combat.result").counters().isEmpty())
    }
}
