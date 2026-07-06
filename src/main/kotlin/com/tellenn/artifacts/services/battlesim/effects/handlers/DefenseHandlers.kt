package com.tellenn.artifacts.services.battlesim.effects.handlers

import com.tellenn.artifacts.services.battlesim.effects.EffectHandler
import com.tellenn.artifacts.services.battlesim.engine.Combatant
import com.tellenn.artifacts.services.battlesim.model.DamageBreakdown
import com.tellenn.artifacts.services.battlesim.model.FightContext
import kotlin.math.roundToInt
import org.springframework.stereotype.Component

@Component
class SunShieldHandler : EffectHandler {
    override val code = "sun_shield"
    override fun modifyIncomingDamage(
        ctx: FightContext, defender: Combatant, attacker: Combatant,
        dmg: DamageBreakdown, value: Int,
    ): DamageBreakdown {
        if (!defender.firstHitTakenThisTurn) return dmg
        val f = 1 - value / 100.0
        return DamageBreakdown(
            (dmg.fire * f).roundToInt(), (dmg.earth * f).roundToInt(),
            (dmg.water * f).roundToInt(), (dmg.air * f).roundToInt(),
        )
    }
}

@Component
class BarrierHandler : EffectHandler {
    override val code = "barrier"
    override fun onFightStart(ctx: FightContext, owner: Combatant, value: Int) { owner.barrierHp += value }
    override fun modifyIncomingDamage(
        ctx: FightContext, defender: Combatant, attacker: Combatant,
        dmg: DamageBreakdown, value: Int,
    ): DamageBreakdown {
        if (defender.barrierHp <= 0) return dmg
        val absorbed = minOf(defender.barrierHp, dmg.total)
        defender.barrierHp -= absorbed
        val remaining = dmg.total - absorbed
        return DamageBreakdown(fire = remaining) // collapse remainder into one bucket; total is what matters
    }
}

/** Boss/raid only: when dropping below 40% HP once, grant value% resistance for 3 turns. */
@Component
class ShellHandler : EffectHandler {
    override val code = "shell"
    override fun onDamageTaken(
        ctx: FightContext, defender: Combatant, attacker: Combatant, dealt: Int, value: Int,
    ) {
        if (!defender.shellUsed && defender.hpRatio() < 0.4) {
            defender.shellUsed = true
            defender.shellResTurnsLeft = 3
            defender.shellPendingRes = value
        }
    }
}

@Component
class CorruptedHandler : EffectHandler {
    override val code = "corrupted"
    override fun modifyIncomingDamage(
        ctx: FightContext, defender: Combatant, attacker: Combatant,
        dmg: DamageBreakdown, value: Int,
    ): DamageBreakdown {
        if (dmg.fire > 0) defender.resFire -= value
        if (dmg.earth > 0) defender.resEarth -= value
        if (dmg.water > 0) defender.resWater -= value
        if (dmg.air > 0) defender.resAir -= value
        return dmg
    }
}

@Component
class EnchantedMirrorHandler : EffectHandler {
    override val code = "enchanted_mirror"
    override fun onDamageTaken(
        ctx: FightContext, defender: Combatant, attacker: Combatant, dealt: Int, value: Int,
    ) {
        if (ctx.turn - defender.lastMirrorTurn >= 3) {
            defender.lastMirrorTurn = ctx.turn
            val reflected = dealt * value / 100
            attacker.hp = (attacker.hp - reflected).coerceAtLeast(0)
        }
    }
}

/** Random elemental resistance (value%) cycling each turn. */
@Component
class ProtectiveBubbleHandler : EffectHandler {
    override val code = "protective_bubble"
    override fun modifyIncomingDamage(
        ctx: FightContext, defender: Combatant, attacker: Combatant,
        dmg: DamageBreakdown, value: Int,
    ): DamageBreakdown {
        val f = 1 - value / 100.0
        return when (ctx.turn % 4) {
            0 -> dmg.copy(fire = (dmg.fire * f).roundToInt())
            1 -> dmg.copy(earth = (dmg.earth * f).roundToInt())
            2 -> dmg.copy(water = (dmg.water * f).roundToInt())
            else -> dmg.copy(air = (dmg.air * f).roundToInt())
        }
    }
}
