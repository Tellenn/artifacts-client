package com.tellenn.artifacts.services.battlesim.effects.handlers

import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.engine.Combatant
import com.tellenn.artifacts.services.battlesim.model.FightContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BuffHandlersTest {
    private fun ctx() = FightContext(mutableListOf(), Random(1))
    private fun combatant() = Combatant.fromMonster(
        MonsterData("m", "m", 1, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0, emptyList(), 0, 0, null, 0, null)
    )

    @Test
    fun `boost_hp raises current and max hp`() {
        val c = combatant()
        BoostHpHandler().onFightStart(ctx(), c, 30)
        assertEquals(130, c.maxHp)
        assertEquals(130, c.hp)
    }

    @Test
    fun `boost_dmg_fire raises fire damage percent`() {
        val c = combatant()
        BoostDmgFireHandler().onFightStart(ctx(), c, 20)
        assertEquals(20, c.dmgFire)
    }

    @Test
    fun `boost_res_air raises air resistance`() {
        val c = combatant()
        BoostResAirHandler().onFightStart(ctx(), c, 15)
        assertEquals(15, c.resAir)
    }
}
