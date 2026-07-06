package com.tellenn.artifacts.services.battlesim

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.SimulateClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.SimulationResult
import com.tellenn.artifacts.models.ArtifactsCharacter
import org.springframework.stereotype.Service

/**
 * Délègue la simulation de combat à l'API du jeu (`/simulation/fight`).
 *
 * NOTE : l'ancienne simulation locale (`simulate`) a été retirée — elle ne modélisait ni le poison
 * ni l'antipoison, ce qui rendait la sélection d'antidote inopérante. Un simulateur local plus fidèle
 * sera réintroduit ultérieurement. En attendant, tout passe par l'API, qui est limitée à ~1 req/s :
 * les appelants doivent limiter le nombre d'appels (le back-off/cooldown est géré par BaseArtifactsClient).
 */
@Service
class BattleSimulatorService(
    private val simulateClient: SimulateClient,
    private val accountClient: AccountClient
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
}
