package com.tellenn.artifacts.services.battlesim.engine

import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.effects.EffectRegistry
import com.tellenn.artifacts.services.battlesim.effects.handlers.PoisonHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class FightEngineTest {
    private val engine = FightEngine(EffectRegistry(listOf(PoisonHandler())))

    private fun monster(hp: Int, attackFire: Int, initiative: Int) = Combatant.fromMonster(
        MonsterData("m", "m", 1, hp, attackFire, 0, 0, 0, 0, 0, 0, 0, 0, emptyList(), 0, 0, null, initiative, null)
    )
    private fun hero(hp: Int, attackFire: Int, initiative: Int): Combatant {
        val c = monster(hp, attackFire, initiative)
        return Combatant(
            name = "hero", isMonster = false, hp = hp, maxHp = hp,
            attackFire = attackFire, attackEarth = 0, attackWater = 0, attackAir = 0,
            dmgGlobal = 0, dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0,
            resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
            criticalStrike = 0, initiative = initiative, threat = 0, effects = emptyList(),
        )
    }

    @Test
    fun `character with higher damage and initiative wins deterministically`() {
        val hero = hero(hp = 100, attackFire = 30, initiative = 100)
        val mob = monster(hp = 50, attackFire = 5, initiative = 1)
        val outcome = engine.run(listOf(hero), mob, Random(1))
        assertTrue(outcome.charactersWin)
        assertEquals(2, outcome.turns) // 50hp / 30 dmg = 2 hero turns
    }

    @Test
    fun `character loses when outclassed`() {
        val hero = hero(hp = 20, attackFire = 1, initiative = 1)
        val mob = monster(hp = 500, attackFire = 30, initiative = 100)
        val outcome = engine.run(listOf(hero), mob, Random(1))
        assertFalse(outcome.charactersWin)
    }
}
