package com.tellenn.artifacts.services.battlesim.effects.handlers

import com.tellenn.artifacts.services.battlesim.effects.EffectHandler
import com.tellenn.artifacts.services.battlesim.engine.Combatant
import com.tellenn.artifacts.services.battlesim.model.FightContext
import org.springframework.stereotype.Component

/**
 * Poison: on the owner's first turn, applies a `value` poison stack to the opponent.
 * The stack then deals `value` HP damage at the start of each poisoned combatant's turn
 * (applied by FightEngine before the owner acts, via [tickPoison]).
 */
@Component
class PoisonHandler : EffectHandler {
    override val code = "poison"
    override fun onTurnStart(ctx: FightContext, owner: Combatant, value: Int) {
        if (owner.turnsPlayed == 0) {
            ctx.enemies(owner).forEach { it.poisonStack += value }
            ctx.log.add("${owner.name} poisons opponents for $value")
        }
    }
}

@Component
class AntipoisonHandler : EffectHandler {
    override val code = "antipoison"
    override fun onTurnStart(ctx: FightContext, owner: Combatant, value: Int) {
        if (owner.poisonStack > 0) {
            owner.poisonStack = (owner.poisonStack - value).coerceAtLeast(0)
        }
    }
}

/**
 * Burn: on the owner's first turn, applies a burn to the opponent equal to `value`% of the
 * owner's total attack. It deals that damage each turn and decreases by 10% each following turn.
 */
@Component
class BurnHandler : EffectHandler {
    override val code = "burn"
    override fun onTurnStart(ctx: FightContext, owner: Combatant, value: Int) {
        if (owner.turnsPlayed == 0) {
            val totalAttack = owner.attackFire + owner.attackEarth + owner.attackWater + owner.attackAir
            val initial = totalAttack * value / 100
            ctx.enemies(owner).forEach { it.burnDamageLeft = initial }
            ctx.log.add("${owner.name} burns opponents for $initial")
        }
    }
}
