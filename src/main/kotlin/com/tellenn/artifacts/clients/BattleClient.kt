package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.requests.CombatRequest
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.CombatResponseBody
import org.springframework.stereotype.Component

@Component
class BattleClient(deps: BaseClientDependencies) : BaseArtifactsClient(deps) {

    fun fight(characterName: String): ArtifactsResponseBody<CombatResponseBody> {
        waitForCooldown(characterName)
        return sendPostRequest("/my/$characterName/action/fight", "{}").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<CombatResponseBody>>(responseBody)
        }
    }

    fun fightBoss(characterName1: String, characterName2: String, characterName3: String): ArtifactsResponseBody<CombatResponseBody>  {
        waitForCooldown(characterName1)
        waitForCooldown(characterName2)
        waitForCooldown(characterName3)
        val request = CombatRequest(listOf(characterName2, characterName3))
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName1/action/fight", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<CombatResponseBody>>(responseBody)
        }
    }
}