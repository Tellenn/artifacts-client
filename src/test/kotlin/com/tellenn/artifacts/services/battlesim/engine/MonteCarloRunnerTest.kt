package com.tellenn.artifacts.services.battlesim.engine

import com.tellenn.artifacts.db.repositories.MonsterRepository
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.TestCharacters
import com.tellenn.artifacts.services.battlesim.effects.EffectRegistry
import com.tellenn.artifacts.services.battlesim.loadout.CombatEffectResolver
import com.tellenn.artifacts.services.battlesim.loadout.ResolvedLoadout
import org.junit.jupiter.api.Assertions.assertEquals
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
    @Test
    fun `runs N deterministic fights and aggregates a 100 percent winrate`() {
        val monsterRepository = mock(MonsterRepository::class.java)
        val resolver = mock(CombatEffectResolver::class.java)
        `when`(monsterRepository.findByCode("m")).thenReturn(
            MonsterData("m", "m", 1, 10, 1, 0, 0, 0, 0, 0, 0, 0, 0, emptyList(), 0, 0, null, 1, null)
        )
        `when`(resolver.resolve(anyObject())).thenReturn(ResolvedLoadout(emptyList(), null, null))

        val runner = MonteCarloRunner(FightEngine(EffectRegistry(emptyList())), resolver, monsterRepository)
        val hero = TestCharacters.blank().apply { attackFire = 50; maxHp = 100; hp = 100; initiative = 100 }

        val result = runner.run("m", listOf(hero), runs = 10, seed = 42L)

        assertEquals(10, result.wins)
        assertEquals(0, result.losses)
        assertEquals(100, result.winrate)
    }
}
