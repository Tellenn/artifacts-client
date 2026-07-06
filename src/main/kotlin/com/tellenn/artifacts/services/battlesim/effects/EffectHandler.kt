package com.tellenn.artifacts.services.battlesim.effects

import com.tellenn.artifacts.services.battlesim.engine.Combatant
import com.tellenn.artifacts.services.battlesim.model.DamageBreakdown
import com.tellenn.artifacts.services.battlesim.model.FightContext

interface EffectHandler {
    val code: String
    fun onFightStart(ctx: FightContext, owner: Combatant, value: Int) {}
    fun onTurnStart(ctx: FightContext, owner: Combatant, value: Int) {}
    fun modifyIncomingDamage(
        ctx: FightContext, defender: Combatant, attacker: Combatant,
        dmg: DamageBreakdown, value: Int,
    ): DamageBreakdown = dmg
    fun onDamageTaken(
        ctx: FightContext, defender: Combatant, attacker: Combatant, dealt: Int, value: Int,
    ) {}
    fun onCritical(
        ctx: FightContext, attacker: Combatant, defender: Combatant,
        dmg: DamageBreakdown, value: Int,
    ) {}
}
