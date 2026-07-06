package com.tellenn.artifacts.services.battlesim.engine

import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.model.ActiveEffect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CombatantTest {
    private fun monster(hp: Int) = MonsterData(
        name = "m", code = "m", level = 1, hp = hp,
        attackFire = 5, attackEarth = 0, attackWater = 0, attackAir = 0,
        defenseFire = 0, defenseEarth = 0, defenseWater = 0, defenseAir = 0,
        criticalStrike = 0, effects = emptyList(), minGold = 0, maxGold = 0,
        drops = null, initiative = 10, type = null,
    )

    @Test
    fun `fromMonster maps hp attack and initiative`() {
        val c = Combatant.fromMonster(monster(120))
        assertEquals(120, c.hp)
        assertEquals(120, c.maxHp)
        assertEquals(5, c.attackFire)
        assertEquals(10, c.initiative)
        assertEquals(0, c.dmgGlobal)
        assertTrue(c.isAlive)
    }

    @Test
    fun `hpRatio reflects current over max`() {
        val c = Combatant.fromMonster(monster(100))
        c.hp = 40
        assertEquals(0.4, c.hpRatio(), 0.0001)
    }
}
