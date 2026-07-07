package com.tellenn.artifacts.services.battlesim.engine

import com.tellenn.artifacts.db.repositories.MonsterRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.TestCharacters
import com.tellenn.artifacts.services.battlesim.effects.EffectRegistry
import com.tellenn.artifacts.services.battlesim.loadout.CombatEffectResolver
import com.tellenn.artifacts.services.battlesim.loadout.ResolvedLoadout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Helper null-safe pour les matchers Mockito sur des paramètres Kotlin non-nullables :
 * `Mockito.any()` renvoie `null`, ce que l'assertion de non-nullité Kotlin rejette.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

class MonteCarloRunnerTest {

    private fun monster(hp: Int, attackFire: Int, initiative: Int, criticalStrike: Int = 0) =
        MonsterData("m", "m", 1, hp, attackFire, 0, 0, 0, 0, 0, 0, 0, criticalStrike, emptyList(), 0, 0, null, initiative, null)

    private fun hero(attackFire: Int, hp: Int, initiative: Int): ArtifactsCharacter =
        TestCharacters.blank().apply {
            this.attackFire = attackFire; maxHp = hp; this.hp = hp; this.initiative = initiative
        }

    /** Runner with mocked repository + resolver, so fights run purely on the local engine. */
    private fun runnerFor(monster: MonsterData): MonteCarloRunner {
        val monsterRepository = mock(MonsterRepository::class.java)
        val resolver = mock(CombatEffectResolver::class.java)
        `when`(monsterRepository.findByCode(monster.code)).thenReturn(monster)
        `when`(resolver.resolve(anyObject())).thenReturn(ResolvedLoadout(emptyList(), null, null))
        return MonteCarloRunner(FightEngine(EffectRegistry(emptyList())), resolver, monsterRepository)
    }

    @Test
    fun `runs N fights and aggregates a 100 percent winrate`() {
        val runner = runnerFor(monster(hp = 10, attackFire = 1, initiative = 1))
        val hero = hero(attackFire = 50, hp = 100, initiative = 100)

        val result = runner.run("m", listOf(hero), runs = 10, seed = 42L)

        assertEquals(10, result.wins)
        assertEquals(0, result.losses)
        assertEquals(100, result.winrate)
    }

    @Test
    fun `aggregates a total loss when the monster is overwhelming`() {
        val runner = runnerFor(monster(hp = 500, attackFire = 100, initiative = 100))
        val hero = hero(attackFire = 1, hp = 10, initiative = 1)

        val result = runner.run("m", listOf(hero), runs = 10, seed = 5L)

        assertEquals(0, result.wins)
        assertEquals(10, result.losses)
        assertEquals(0, result.winrate)
    }

    @Test
    fun `same seed produces identical results across the whole batch`() {
        // A crit-bearing monster in a close fight makes each run genuinely RNG-dependent, so equality
        // of two same-seed batches is a real reproducibility check, not a vacuous one.
        val runner = runnerFor(monster(hp = 40, attackFire = 12, initiative = 1, criticalStrike = 50))
        val hero = hero(attackFire = 8, hp = 60, initiative = 100)

        val first = runner.run("m", listOf(hero), runs = 20, seed = 77L)
        val second = runner.run("m", listOf(hero), runs = 20, seed = 77L)

        assertEquals(first, second)
    }

    @Test
    fun `rejects a non-positive run count`() {
        val runner = runnerFor(monster(hp = 10, attackFire = 1, initiative = 1))
        val hero = hero(attackFire = 50, hp = 100, initiative = 100)

        assertThrows(IllegalArgumentException::class.java) {
            runner.run("m", listOf(hero), runs = 0, seed = 1L)
        }
    }
}
