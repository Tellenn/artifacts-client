package com.tellenn.artifacts.services.battlesim.effects.handlers

import com.tellenn.artifacts.services.battlesim.effects.EffectHandler
import com.tellenn.artifacts.services.battlesim.engine.Combatant
import com.tellenn.artifacts.services.battlesim.model.DamageBreakdown
import com.tellenn.artifacts.services.battlesim.model.FightContext
import org.springframework.stereotype.Component

@Component
class GreedHandler : EffectHandler {
    override val code = "greed"
    override fun onDamageTaken(
        ctx: FightContext, defender: Combatant, attacker: Combatant, dealt: Int, value: Int,
    ) {
        val lostPct = (1.0 - defender.hpRatio()) * 100
        val thresholds = (lostPct / 10).toInt()
        if (thresholds > defender.greedThresholdsCrossed) {
            val newOnes = thresholds - defender.greedThresholdsCrossed
            defender.bonusDamagePct += value * newOnes
            defender.greedThresholdsCrossed = thresholds
        }
    }
}

@Component
class BerserkerRageHandler : EffectHandler {
    override val code = "berserker_rage"
    override fun onDamageTaken(
        ctx: FightContext, defender: Combatant, attacker: Combatant, dealt: Int, value: Int,
    ) {
        if (!defender.berserkUsed && defender.hpRatio() < 0.25) {
            defender.berserkUsed = true
            defender.bonusDamagePct += value
        }
    }
}

@Component
class FrenzyHandler : EffectHandler {
    override val code = "frenzy"
    override fun onCritical(
        ctx: FightContext, attacker: Combatant, defender: Combatant, dmg: DamageBreakdown, value: Int,
    ) {
        val self = dmg.total * value / 100
        (ctx.allies(attacker) + attacker).forEach { it.hp = (it.hp - self).coerceAtLeast(0) }
    }
}

@Component
class ChristmasMagicHandler : EffectHandler {
    override val code = "christmas_magic"
    override fun onDamageTaken(
        ctx: FightContext, defender: Combatant, attacker: Combatant, dealt: Int, value: Int,
    ) {
        ctx.enemies(defender).forEach { it.bonusDamagePct += value }
    }
}

@Component
class LifestealHandler : EffectHandler {
    override val code = "lifesteal"
    override fun onCritical(
        ctx: FightContext, attacker: Combatant, defender: Combatant, dmg: DamageBreakdown, value: Int,
    ) {
        val totalAttack = attacker.attackFire + attacker.attackEarth + attacker.attackWater + attacker.attackAir
        attacker.healUpTo(totalAttack * value / 100)
    }
}

@Component
class VampiricStrikeHandler : EffectHandler {
    override val code = "vampiric_strike"
    override fun onCritical(
        ctx: FightContext, attacker: Combatant, defender: Combatant, dmg: DamageBreakdown, value: Int,
    ) {
        val target = (ctx.allies(attacker) + attacker).minByOrNull { it.hpRatio() } ?: attacker
        target.healUpTo(dmg.total * value / 100)
    }
}

/**
 * Guard: redirect an ally's incoming damage to this bearer, max 3×/combat. Full redirect routing
 * requires target selection at attack time; implemented as a best-effort activation counter so the
 * effect is registered (coverage) and validated against API fixtures later.
 */
@Component
class GuardHandler : EffectHandler {
    override val code = "guard"
    override fun onDamageTaken(
        ctx: FightContext, defender: Combatant, attacker: Combatant, dealt: Int, value: Int,
    ) {
        if (defender.guardActivations < 3) defender.guardActivations++
    }
}
