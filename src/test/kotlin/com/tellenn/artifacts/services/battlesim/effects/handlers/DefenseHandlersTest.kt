package com.tellenn.artifacts.services.battlesim.effects.handlers

import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.engine.Combatant
import com.tellenn.artifacts.services.battlesim.model.DamageBreakdown
import com.tellenn.artifacts.services.battlesim.model.FightContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DefenseHandlersTest {
    private fun mob() = Combatant.fromMonster(
        MonsterData("m", "m", 1, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0, emptyList(), 0, 0, null, 0, null)
    )
    private fun ctx() = FightContext(mutableListOf(), Random(1)).apply {
        characters = emptyList(); monsters = emptyList()
    }

    @Test
    fun `sun_shield halves the first hit of the turn`() {
        val d = mob(); d.firstHitTakenThisTurn = true
        val out = SunShieldHandler().modifyIncomingDamage(ctx(), d, mob(), DamageBreakdown(fire = 40), 50)
        assertEquals(20, out.total)
    }

    @Test
    fun `barrier absorbs damage before hp`() {
        val d = mob(); d.barrierHp = 30
        val out = BarrierHandler().modifyIncomingDamage(ctx(), d, mob(), DamageBreakdown(fire = 50), 0)
        assertEquals(20, out.total)   // 50 - 30 barrier
        assertEquals(0, d.barrierHp)
    }

    @Test
    fun `corrupted lowers hit element resistance`() {
        val d = mob(); d.resFire = 10
        CorruptedHandler().modifyIncomingDamage(ctx(), d, mob(), DamageBreakdown(fire = 5), 20)
        assertEquals(-10, d.resFire)  // 10 - 20
    }
}
