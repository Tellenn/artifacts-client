package com.tellenn.artifacts.services.battlesim.effects.handlers

import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.engine.Combatant
import com.tellenn.artifacts.services.battlesim.model.FightContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class HealHandlersTest {
    private fun mob() = Combatant.fromMonster(
        MonsterData("m", "m", 1, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0, emptyList(), 0, 0, null, 0, null)
    )
    private fun ctx() = FightContext(mutableListOf(), Random(1)).apply {
        characters = emptyList(); monsters = emptyList()
    }

    @Test
    fun `restore heals only when below 50 percent`() {
        val c = mob(); c.hp = 40
        RestoreHandler().onTurnStart(ctx(), c, 15)
        assertEquals(55, c.hp)
    }

    @Test
    fun `restore does nothing at or above 50 percent`() {
        val c = mob(); c.hp = 60
        RestoreHandler().onTurnStart(ctx(), c, 15)
        assertEquals(60, c.hp)
    }

    @Test
    fun `healing heals percent every 3 turns`() {
        val c = mob(); c.hp = 50; c.turnsPlayed = 3
        HealingHandler().onTurnStart(ctx(), c, 20) // 20% of 100 = 20
        assertEquals(70, c.hp)
    }

    @Test
    fun `reconstitution regains full hp every value turns`() {
        val c = mob(); c.hp = 10; c.turnsPlayed = 5
        ReconstitutionHandler().onTurnStart(ctx(), c, 5)
        assertEquals(100, c.hp)
    }
}
