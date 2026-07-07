package com.tellenn.artifacts.services.battlesim.engine

import com.tellenn.artifacts.db.repositories.MonsterRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.battlesim.loadout.CombatEffectResolver
import com.tellenn.artifacts.services.battlesim.model.FightOutcome
import com.tellenn.artifacts.services.battlesim.model.LocalSimulationResult
import com.tellenn.artifacts.services.battlesim.model.copyFresh
import org.springframework.stereotype.Service
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Exécute N simulations de combat indépendantes (Monte-Carlo) hors ligne, sans appel API,
 * et agrège le taux de victoire ainsi que la durée moyenne des combats.
 */
@Service
class MonteCarloRunner(
    private val fightEngine: FightEngine,
    private val combatEffectResolver: CombatEffectResolver,
    private val monsterRepository: MonsterRepository,
) {
    fun run(
        monsterCode: String,
        characters: List<ArtifactsCharacter>,
        runs: Int,
        seed: Long?,
    ): LocalSimulationResult {
        require(runs > 0) { "runs must be > 0" }
        val monsterData = monsterRepository.findByCode(monsterCode)
        val loadouts = characters.map { it to combatEffectResolver.resolve(it) }
        val rng = Random(seed ?: System.nanoTime())

        val outcomes = ArrayList<FightOutcome>(runs)
        repeat(runs) {
            val combatants = loadouts.map { (c, l) ->
                Combatant.fromCharacter(c, l.effects, l.healPotion1?.copyFresh(), l.healPotion2?.copyFresh())
            }
            outcomes.add(fightEngine.run(combatants, Combatant.fromMonster(monsterData), rng))
        }

        val wins = outcomes.count { it.charactersWin }
        return LocalSimulationResult(
            wins = wins,
            losses = runs - wins,
            winrate = (wins * 100.0 / runs).roundToInt(),
            avgTurns = outcomes.map { it.turns }.average(),
            results = outcomes,
        )
    }
}
