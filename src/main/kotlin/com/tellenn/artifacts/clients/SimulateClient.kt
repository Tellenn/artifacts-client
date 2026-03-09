package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.requests.SimulationBattleRequest
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.SimulationResult
import com.tellenn.artifacts.models.ArtifactsCharacter
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class SimulateClient : BaseArtifactsClient() {

    fun simulate1v1(character: ArtifactsCharacter, monsterName: String): ArtifactsResponseBody<SimulationResult> {
        character.utility1SlotQuantity = 1
        character.utility2SlotQuantity = 1
        val req = SimulationBattleRequest(listOf(character), monsterName, 10)
        return sendPostRequest("/simulation/fight_simulation", objectMapper.writeValueAsString(req)).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<SimulationResult>>(responseBody)
        }
    }
}