package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.requests.SimulationBattleRequest
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.SimulationResult
import com.tellenn.artifacts.models.ArtifactsCharacter
import org.springframework.stereotype.Component

@Component
class SimulateClient(
    deps: BaseClientDependencies,
    private val simulationRateLimiter: SimulationRateLimiter,
) : BaseArtifactsClient(deps) {

    fun simulate(characters: List<ArtifactsCharacter>, monsterName: String): ArtifactsResponseBody<SimulationResult> {
        val chars = characters.map {
            if(it.utility1SlotQuantity == 0) it.utility1SlotQuantity = 1
            if(it.utility2SlotQuantity == 0) it.utility2SlotQuantity = 1
            it
        }

        // L'endpoint est limité à 1 req/s : on temporise avant l'appel pour ne pas déclencher de 429.
        simulationRateLimiter.acquire()
        val req = SimulationBattleRequest(chars, monsterName, 10)
        return sendPostRequest("/simulation/fight", objectMapper.writeValueAsString(req)).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<SimulationResult>>(responseBody)
        }
    }
}