package com.tellenn.artifacts.services.battlesim.effects

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.services.battlesim.effects.handlers.*
import com.tellenn.artifacts.services.battlesim.loadout.CombatEffectResolver
import org.junit.jupiter.api.Assertions.assertEquals
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

    /**
     * The resolver's behavioural-effect allowlist is a third source of truth (alongside the handlers
     * and the snapshot). Guard against drift: a behavioural effect added with a handler but forgotten
     * in [CombatEffectResolver.COMBAT_EFFECT_CODES] would be silently dropped from equipment loadouts.
     * The allowlist must equal exactly the non-`boost_` handler codes (`boost_*` come from utilities,
     * not equipment; `heal` is engine-handled and has no handler).
     */
    @Test
    fun `resolver allowlist stays in sync with the non-boost handler codes`() {
        val nonBoostHandlerCodes = allHandlers().map { it.code }.filterNot { it.startsWith("boost_") }.toSet()

        assertEquals(nonBoostHandlerCodes, CombatEffectResolver.COMBAT_EFFECT_CODES)
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
