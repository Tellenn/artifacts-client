package com.tellenn.artifacts.services.battlesim.engine

import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.model.ActiveEffect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DamageCalculatorTest {

    private fun bareMonster(attackFire: Int = 0, resFire: Int = 0) = MonsterData(
        name = "m", code = "m", level = 1, hp = 100,
        attackFire = attackFire, attackEarth = 0, attackWater = 0, attackAir = 0,
        defenseFire = resFire, defenseEarth = 0, defenseWater = 0, defenseAir = 0,
        criticalStrike = 0, effects = emptyList(), minGold = 0, maxGold = 0,
        drops = null, initiative = 0, type = null,
    )

    @Test
    fun `elementDamage applies damage percent then resistance with half-up rounding`() {
        // attack 50, dmg 10% -> round(55) = 55, res 30% -> round(38.5) = 39
        assertEquals(39, DamageCalculator.elementDamage(attack = 50, dmgPct = 10, res = 30))
    }

    @Test
    fun `elementDamage with negative resistance increases damage`() {
        // attack 20, dmg 0 -> 20, res -50% -> round(30) = 30
        assertEquals(30, DamageCalculator.elementDamage(attack = 20, dmgPct = 0, res = -50))
    }

    @Test
    fun `elementDamage never negative`() {
        assertEquals(0, DamageCalculator.elementDamage(attack = 0, dmgPct = 0, res = 0))
    }

    @Test
    fun `computeHit sums elements and applies crit`() {
        val attacker = Combatant.fromMonster(bareMonster(attackFire = 40))
        val defender = Combatant.fromMonster(bareMonster(resFire = 0))
        val normal = DamageCalculator.computeHit(attacker, defender, critical = false)
        assertEquals(40, normal.total)
        val crit = DamageCalculator.computeHit(attacker, defender, critical = true)
        assertEquals(60, crit.total) // round(40*1.5)
    }
}
