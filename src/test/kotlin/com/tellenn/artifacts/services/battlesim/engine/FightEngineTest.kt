package com.tellenn.artifacts.services.battlesim.engine

import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.effects.EffectRegistry
import com.tellenn.artifacts.services.battlesim.effects.handlers.PoisonHandler
import com.tellenn.artifacts.services.battlesim.effects.handlers.RestoreHandler
import com.tellenn.artifacts.services.battlesim.model.ActiveEffect
import com.tellenn.artifacts.services.battlesim.model.HealPotion
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

    private fun hero(
        hp: Int,
        attackFire: Int,
        initiative: Int,
        threat: Int = 0,
        name: String = "hero",
        healPotion1: HealPotion? = null,
    ): Combatant = Combatant(
        name = name, isMonster = false, hp = hp, maxHp = hp,
        attackFire = attackFire, attackEarth = 0, attackWater = 0, attackAir = 0,
        dmgGlobal = 0, dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0,
        resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
        criticalStrike = 0, initiative = initiative, threat = threat, effects = emptyList(),
        healPotion1 = healPotion1,
    )

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

    @Test
    fun `character below 50 percent HP consumes a heal potion`() {
        // Mob (initiative 100) hits first for 60, dropping the 100-HP hero to 40% before the hero acts.
        val hero = hero(
            hp = 100, attackFire = 100, initiative = 1,
            healPotion1 = HealPotion("hp_potion", 30, 2),
        )
        val mob = monster(hp = 50, attackFire = 60, initiative = 100)

        val outcome = engine.run(listOf(hero), mob, Random(1))

        assertTrue(outcome.charactersWin)
        assertEquals(1, hero.healPotion1?.remaining) // 2 → 1, one potion consumed
        assertEquals(70, outcome.finalHp["hero"]) // 100 - 60 hit + 30 heal (no potion would be 40)
    }

    @Test
    fun `combatant killed by damage-over-time never gets to act`() {
        // Poison stack (10) exceeds the mob's 5 HP; the DoT tick at its turn start kills it first.
        val hero = hero(hp = 100, attackFire = 1, initiative = 1)
        val mob = monster(hp = 5, attackFire = 50, initiative = 100).apply { poisonStack = 10 }

        val outcome = engine.run(listOf(hero), mob, Random(1))

        assertTrue(outcome.charactersWin)
        assertEquals(0, outcome.finalHp["m"])
        assertEquals(100, outcome.finalHp["hero"]) // mob died to poison before landing its 50-dmg hit
    }

    @Test
    fun `damage-over-time death is not undone by a same-turn self-heal`() {
        // The mob's own `restore` would heal it back above 0 if the engine let it act after the DoT
        // tick — a heal-bearing monster would become un-poisonable. The poison must land the kill.
        val engineWithHeal = FightEngine(EffectRegistry(listOf(PoisonHandler(), RestoreHandler())))
        val hero = hero(hp = 100, attackFire = 1, initiative = 1)
        val mob = Combatant(
            name = "healer", isMonster = true, hp = 5, maxHp = 40,
            attackFire = 50, attackEarth = 0, attackWater = 0, attackAir = 0,
            dmgGlobal = 0, dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0,
            resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
            criticalStrike = 0, initiative = 100, threat = 0,
            effects = listOf(ActiveEffect("restore", 50)),
        ).apply { poisonStack = 10 }

        val outcome = engineWithHeal.run(listOf(hero), mob, Random(1))

        assertTrue(outcome.charactersWin)
        assertEquals(0, outcome.finalHp["healer"])
        assertEquals(100, outcome.finalHp["hero"]) // mob dies to poison before it can land its 50-dmg hit
    }

    @Test
    fun `monster targets the highest-threat character about 90 percent of the time`() {
        var highThreatHits = 0
        var lowThreatHits = 0
        val trials = 400

        repeat(trials) { seed ->
            // Mob (initiative 100) lands exactly one hit before the two heroes kill it the same turn.
            // Distinct starting HP (100 vs 60) makes the 10% min-HP branch resolve to squishy, not the
            // first-listed hero, so the two targeting branches are actually distinguishable.
            val tank = hero(hp = 100, attackFire = 60, initiative = 50, threat = 100, name = "tank")
            val squishy = hero(hp = 60, attackFire = 60, initiative = 40, threat = 0, name = "squishy")
            val mob = monster(hp = 100, attackFire = 20, initiative = 100)

            val outcome = engine.run(listOf(tank, squishy), mob, Random(seed.toLong()))

            if (outcome.finalHp["tank"]!! < 100) highThreatHits++
            if (outcome.finalHp["squishy"]!! < 60) lowThreatHits++
        }

        assertEquals(trials, highThreatHits + lowThreatHits) // exactly one hero hit per fight
        assertTrue(highThreatHits in 320..399) { "high-threat hits=$highThreatHits (expected ~360)" }
        assertTrue(lowThreatHits > 5) { "low-threat branch never exercised: hits=$lowThreatHits" }
    }

    @Test
    fun `stalemate reaching the turn cap counts as a loss`() {
        // Neither side deals damage → the fight runs the full 100 turns and the characters lose.
        val hero = hero(hp = 100, attackFire = 0, initiative = 50)
        val mob = monster(hp = 100, attackFire = 0, initiative = 1)

        val outcome = engine.run(listOf(hero), mob, Random(1))

        assertFalse(outcome.charactersWin)
        assertEquals(100, outcome.turns)
    }
}
