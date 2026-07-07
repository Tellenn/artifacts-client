package com.tellenn.artifacts.services.battlesim.effects

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.services.battlesim.effects.handlers.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EffectCoverageTest {

    /** Every combat/consumable effect from the API snapshot must have a handler. */
    @Test
    fun `every combat effect in the snapshot has a registered handler`() {
        val json = javaClass.getResourceAsStream("/battlesim/effects-snapshot.json")!!
            .readBytes().decodeToString()
        val snapshot: Map<String, List<String>> = jacksonObjectMapper().readValue(json)
        val required = (snapshot["combat"].orEmpty() + snapshot["consumable"].orEmpty()).toSet()

        // `heal` is consumed reactively by FightEngine, not via a handler.
        val handledByEngine = setOf("heal")
        val registry = EffectRegistry(allHandlers())
        val missing = required
            .filter { it !in handledByEngine }
            .filter { registry.handlerFor(it) == null }

        assertTrue(missing.isEmpty(), "Effects without a handler: $missing")
    }

    private fun allHandlers(): List<EffectHandler> = listOf(
        BoostHpHandler(), BoostDmgFireHandler(), BoostDmgEarthHandler(), BoostDmgWaterHandler(),
        BoostDmgAirHandler(), BoostResFireHandler(), BoostResEarthHandler(), BoostResWaterHandler(),
        BoostResAirHandler(), PoisonHandler(), BurnHandler(), AntipoisonHandler(), RestoreHandler(),
        SplashRestoreHandler(), HealingHandler(), ReconstitutionHandler(), VoidDrainHandler(),
        HealingAuraHandler(), SunShieldHandler(), BarrierHandler(), ShellHandler(), CorruptedHandler(),
        EnchantedMirrorHandler(), ProtectiveBubbleHandler(), GreedHandler(), BerserkerRageHandler(),
        FrenzyHandler(), ChristmasMagicHandler(), LifestealHandler(), VampiricStrikeHandler(),
        GuardHandler(),
    )
}
