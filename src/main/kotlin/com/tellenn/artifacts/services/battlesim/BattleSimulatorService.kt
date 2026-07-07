package com.tellenn.artifacts.services.battlesim

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.SimulateClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.SimulationResult
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.battlesim.engine.MonteCarloRunner
import com.tellenn.artifacts.services.battlesim.model.LocalSimulationResult
import org.springframework.stereotype.Service

/**
 * Point d'entrée de la simulation de combat, avec deux stratégies complémentaires :
 * - `simulateLocally` exécute le moteur de combat Monte-Carlo hors ligne (tour par tour, `MonteCarloRunner`),
 *   sans appel réseau ni limite de débit — utile pour des batchs de simulations ou du calcul répété.
 * - `simulateWithApi` / `simulateWithCharacterName` délèguent à l'API du jeu (`/simulation/fight`),
 *   limitée à ~1 req/s : les appelants doivent limiter le nombre d'appels (le back-off/cooldown est
 *   géré par `BaseArtifactsClient`).
 */
@Service
class BattleSimulatorService(
    private val simulateClient: SimulateClient,
    private val accountClient: AccountClient,
    private val monteCarloRunner: MonteCarloRunner,
) {

    fun simulateWithApi(monsterCode: String, character: ArtifactsCharacter): ArtifactsResponseBody<SimulationResult> {
        return simulateWithApi(monsterCode, listOf(character))
    }

    fun simulateWithApi(monsterCode: String, characters: List<ArtifactsCharacter>): ArtifactsResponseBody<SimulationResult> {
        return simulateClient.simulate(characters, monsterCode)
    }

    fun simulateWithCharacterName(monsterCode: String, characterName: String): ArtifactsResponseBody<SimulationResult> {
        val character = accountClient.getCharacter(characterName).data
        return simulateWithApi(monsterCode, character)
    }

    fun simulateLocally(
        monsterCode: String,
        characters: List<ArtifactsCharacter>,
        runs: Int = 10,
        seed: Long? = null,
    ): LocalSimulationResult = monteCarloRunner.run(monsterCode, characters, runs, seed)

    fun simulateLocally(
        monsterCode: String,
        character: ArtifactsCharacter,
        runs: Int = 10,
        seed: Long? = null,
    ): LocalSimulationResult = simulateLocally(monsterCode, listOf(character), runs, seed)
}
