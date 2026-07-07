package com.tellenn.artifacts.services.battlesim

import com.tellenn.artifacts.db.repositories.MonsterRepository
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.effects.EffectRegistry
import com.tellenn.artifacts.services.battlesim.engine.FightEngine
import com.tellenn.artifacts.services.battlesim.engine.MonteCarloRunner
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

class ApiComparisonTest {

    @Test
    fun `deterministic fight matches a known exact outcome`() {
        val monsterRepository = mock(MonsterRepository::class.java)
        val resolver = mock(CombatEffectResolver::class.java)
        `when`(resolver.resolve(anyObject())).thenReturn(ResolvedLoadout(emptyList(), null, null))
        `when`(monsterRepository.findByCode("dummy")).thenReturn(
            MonsterData("dummy", "dummy", 1, 30, 3, 0, 0, 0,
                0, 0, 0, 0, 0, emptyList(), 0, 0, null, 1, null)
        )
        val runner = MonteCarloRunner(FightEngine(EffectRegistry(emptyList())), resolver, monsterRepository)
        val hero = TestCharacters.blank().apply {
            attackFire = 10; maxHp = 100; hp = 100; initiative = 50; criticalStrike = 0
        }

        val result = runner.run("dummy", listOf(hero), runs = 25, seed = 7L)

        assertEquals(25, result.wins)              // deterministic → always wins
        assertEquals(0, result.losses)
        assertEquals(3.0, result.avgTurns, 0.0001) // 30hp / 10dmg = 3 hero turns
    }
}
