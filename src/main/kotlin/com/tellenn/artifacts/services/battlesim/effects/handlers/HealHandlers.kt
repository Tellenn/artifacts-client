package com.tellenn.artifacts.services.battlesim.effects.handlers

import com.tellenn.artifacts.services.battlesim.effects.EffectHandler
import com.tellenn.artifacts.services.battlesim.engine.Combatant
import com.tellenn.artifacts.services.battlesim.model.FightContext
import org.springframework.stereotype.Component

@Component
class RestoreHandler : EffectHandler {
    override val code = "restore"
    override fun onTurnStart(ctx: FightContext, owner: Combatant, value: Int) {
        if (owner.hpRatio() < 0.5) owner.healUpTo(value)
    }
}

/** Restores HP to the ally who lost the most, if that ally is under 50% HP. */
@Component
class SplashRestoreHandler : EffectHandler {
    override val code = "splash_restore"
    override fun onTurnStart(ctx: FightContext, owner: Combatant, value: Int) {
        val target = ctx.allies(owner).filter { it.hpRatio() < 0.5 }.minByOrNull { it.hpRatio() }
        target?.healUpTo(value)
    }
}

/** Every 3 played turns, restores value% of max HP. */
@Component
class HealingHandler : EffectHandler {
    override val code = "healing"
    override fun onTurnStart(ctx: FightContext, owner: Combatant, value: Int) {
        if (owner.turnsPlayed > 0 && owner.turnsPlayed % 3 == 0) {
            owner.healUpTo(owner.maxHp * value / 100)
        }
    }
}

/** Every `value` played turns, regains all HP. */
@Component
class ReconstitutionHandler : EffectHandler {
    override val code = "reconstitution"
    override fun onTurnStart(ctx: FightContext, owner: Combatant, value: Int) {
        if (value > 0 && owner.turnsPlayed > 0 && owner.turnsPlayed % value == 0) {
            owner.hp = owner.maxHp
        }
    }
}

/** Every 4 turns, drains value% HP from each enemy to heal the owner. */
@Component
class VoidDrainHandler : EffectHandler {
    override val code = "void_drain"
    override fun onTurnStart(ctx: FightContext, owner: Combatant, value: Int) {
        if (owner.turnsPlayed > 0 && owner.turnsPlayed % 4 == 0) {
            var drained = 0
            ctx.enemies(owner).forEach {
                val d = it.maxHp * value / 100
                it.hp = (it.hp - d).coerceAtLeast(0)
                drained += d
            }
            owner.healUpTo(drained)
        }
    }
}

/** Every 2 played turns, heals all allies (not the caster) for value% of their max HP. */
@Component
class HealingAuraHandler : EffectHandler {
    override val code = "healing_aura"
    override fun onTurnStart(ctx: FightContext, owner: Combatant, value: Int) {
        if (owner.turnsPlayed > 0 && owner.turnsPlayed % 2 == 0) {
            ctx.allies(owner).forEach { it.healUpTo(it.maxHp * value / 100) }
        }
    }
}
