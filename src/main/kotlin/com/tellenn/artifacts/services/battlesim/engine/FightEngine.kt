package com.tellenn.artifacts.services.battlesim.engine

import com.tellenn.artifacts.services.battlesim.effects.EffectRegistry
import com.tellenn.artifacts.services.battlesim.model.DamageBreakdown
import com.tellenn.artifacts.services.battlesim.model.FightContext
import com.tellenn.artifacts.services.battlesim.model.FightOutcome
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class FightEngine(private val registry: EffectRegistry) {

    companion object { const val MAX_TURNS = 100 }

    fun run(characters: List<Combatant>, monster: Combatant, rng: Random): FightOutcome {
        val ctx = FightContext(mutableListOf(), rng)
        ctx.characters = characters
        ctx.monsters = listOf(monster)
        val all = characters + monster

        val order = all.sortedWith(
            compareByDescending<Combatant> { it.initiative }
                .thenByDescending { it.maxHp }
                .thenBy { rng.nextInt() },
        )

        all.forEach { owner -> owner.effects.forEach { e -> handler(e.code)?.onFightStart(ctx, owner, e.value) } }

        var turns = 0
        for (turn in 1..MAX_TURNS) {
            ctx.turn = turn
            for (owner in order) {
                if (!owner.isAlive) continue
                turns = turn
                owner.firstHitTakenThisTurn = true

                // DoT tick
                if (owner.poisonStack > 0) owner.hp = (owner.hp - owner.poisonStack).coerceAtLeast(0)
                if (owner.burnDamageLeft > 0) {
                    owner.hp = (owner.hp - owner.burnDamageLeft).coerceAtLeast(0)
                    owner.burnDamageLeft = (owner.burnDamageLeft * 0.9).toInt()
                }

                // Death by damage-over-time is final: a combatant killed by the tick above must not
                // be revived by its own start-of-turn heals (e.g. restore/reconstitution).
                if (!owner.isAlive) { if (fightOver(characters, monster)) break else continue }

                owner.effects.forEach { e -> handler(e.code)?.onTurnStart(ctx, owner, e.value) }

                if (!owner.isAlive) { if (fightOver(characters, monster)) break else continue }

                // potion
                if (!owner.isMonster && owner.hpRatio() < 0.5) consumePotion(owner)

                val target = chooseTarget(ctx, owner, rng) ?: break
                val crit = owner.criticalStrike > 0 && rng.nextInt(100) < owner.criticalStrike
                var dmg = DamageCalculator.computeHit(owner, target, crit)
                target.effects.forEach { e ->
                    dmg = handler(e.code)?.modifyIncomingDamage(ctx, target, owner, dmg, e.value) ?: dmg
                }
                target.firstHitTakenThisTurn = false
                val dealt = dmg.total
                target.hp = (target.hp - dealt).coerceAtLeast(0)
                if (crit) owner.effects.forEach { e -> handler(e.code)?.onCritical(ctx, owner, target, dmg, e.value) }
                target.effects.forEach { e -> handler(e.code)?.onDamageTaken(ctx, target, owner, dealt, e.value) }

                owner.turnsPlayed++
                if (fightOver(characters, monster)) break
            }
            if (fightOver(characters, monster)) break
        }

        val win = monster.hp <= 0 && characters.any { it.isAlive }
        return FightOutcome(
            charactersWin = win,
            turns = turns,
            finalHp = (characters + monster).associate { it.name to it.hp },
            logs = ctx.log.toList(),
        )
    }

    private fun handler(code: String) = registry.handlerFor(code)

    private fun fightOver(characters: List<Combatant>, monster: Combatant): Boolean =
        monster.hp <= 0 || characters.none { it.isAlive }

    private fun consumePotion(owner: Combatant) {
        val potion = listOfNotNull(owner.healPotion1, owner.healPotion2).firstOrNull { it.remaining > 0 } ?: return
        owner.healUpTo(potion.healPerUse)
        potion.remaining--
    }

    private fun chooseTarget(ctx: FightContext, owner: Combatant, rng: Random): Combatant? {
        val enemies = ctx.enemies(owner)
        if (enemies.isEmpty()) return null
        if (enemies.size == 1) return enemies.first()
        if (!owner.isMonster) return enemies.first()
        return if (rng.nextInt(100) < 90) {
            val maxThreat = enemies.maxOf { it.threat }
            enemies.filter { it.threat == maxThreat }.let { it[rng.nextInt(it.size)] }
        } else {
            enemies.minByOrNull { it.hp }!!
        }
    }
}
