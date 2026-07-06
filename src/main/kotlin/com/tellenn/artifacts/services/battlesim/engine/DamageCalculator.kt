package com.tellenn.artifacts.services.battlesim.engine

import com.tellenn.artifacts.services.battlesim.model.DamageBreakdown
import kotlin.math.roundToInt

object DamageCalculator {

    fun elementDamage(attack: Int, dmgPct: Int, res: Int): Int {
        if (attack <= 0) return 0
        val afterDmg = (attack * (1 + dmgPct / 100.0)).roundToInt()
        return (afterDmg * (1 - res / 100.0)).roundToInt().coerceAtLeast(0)
    }

    fun computeHit(attacker: Combatant, defender: Combatant, critical: Boolean): DamageBreakdown {
        val g = attacker.dmgGlobal + attacker.bonusDamagePct
        val db = DamageBreakdown(
            fire = elementDamage(attacker.attackFire, attacker.dmgFire + g, defender.resFire),
            earth = elementDamage(attacker.attackEarth, attacker.dmgEarth + g, defender.resEarth),
            water = elementDamage(attacker.attackWater, attacker.dmgWater + g, defender.resWater),
            air = elementDamage(attacker.attackAir, attacker.dmgAir + g, defender.resAir),
        )
        return if (critical) db.crit() else db
    }
}
