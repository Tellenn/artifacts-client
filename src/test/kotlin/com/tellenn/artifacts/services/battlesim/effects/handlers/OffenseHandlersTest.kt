package com.tellenn.artifacts.services.battlesim.effects.handlers

import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.engine.Combatant
import com.tellenn.artifacts.services.battlesim.model.DamageBreakdown
import com.tellenn.artifacts.services.battlesim.model.FightContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class OffenseHandlersTest {
    private fun mob(attackFire: Int = 0) = Combatant.fromMonster(
        MonsterData("m", "m", 1, 100, attackFire, 0, 0, 0, 0, 0, 0, 0, 0, emptyList(), 0, 0, null, 0, null)
    )
    private fun ctx() = FightContext(mutableListOf(), Random(1)).apply {
        characters = emptyList(); monsters = emptyList()
    }

    @Test
    fun `berserker_rage grants damage once below 25 percent`() {
        val c = mob(); c.hp = 20
        val h = BerserkerRageHandler()
        h.onDamageTaken(ctx(), c, mob(), 10, 30)
        h.onDamageTaken(ctx(), c, mob(), 10, 30) // second time: no extra
        assertEquals(30, c.bonusDamagePct)
    }

    @Test
    fun `greed adds damage per 10 percent hp lost`() {
        val c = mob(); c.hp = 100
        val h = GreedHandler()
        c.hp = 75; h.onDamageTaken(ctx(), c, mob(), 25, 5) // 2 thresholds crossed (90,80)
        assertEquals(10, c.bonusDamagePct)
    }

    @Test
    fun `lifesteal heals attacker for percent of total attack on crit`() {
        val a = mob(attackFire = 40); a.hp = 50
        LifestealHandler().onCritical(ctx(), a, mob(), DamageBreakdown(fire = 60), 25)
        assertEquals(60, a.hp) // 25% of 40 = 10
    }
}
