package com.tellenn.artifacts.services.battlesim.effects.handlers

import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.engine.Combatant
import com.tellenn.artifacts.services.battlesim.model.FightContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DamageOverTimeHandlersTest {
    private fun mob(attackFire: Int = 0) = Combatant.fromMonster(
        MonsterData("m", "m", 1, 100, attackFire, 0, 0, 0, 0, 0, 0, 0, 0, emptyList(), 0, 0, null, 0, null)
    )

    private fun ctxWith(owner: Combatant, enemy: Combatant): FightContext {
        val ctx = FightContext(mutableListOf(), Random(1))
        ctx.characters = listOf(enemy)   // enemy is on character side
        ctx.monsters = listOf(owner)     // owner is monster
        return ctx
    }

    @Test
    fun `poison applies a stack to the enemy`() {
        val owner = mob(); val enemy = mob()
        val ctx = ctxWith(owner, enemy)
        val handler = PoisonHandler()
        handler.onTurnStart(ctx, owner, 8)        // owner's first turn: applies 8 poison to enemy
        assertEquals(8, enemy.poisonStack)
    }

    @Test
    fun `antipoison removes poison from its owner`() {
        val owner = mob()
        owner.poisonStack = 10
        val ctx = ctxWith(owner, mob())
        AntipoisonHandler().onTurnStart(ctx, owner, 4)
        assertEquals(6, owner.poisonStack)
    }
}
