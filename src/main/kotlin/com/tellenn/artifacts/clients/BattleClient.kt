package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.models.SimpleItem
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.CombatResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class BattleClient : BaseArtifactsClient() {

    fun fight(characterName: String): ArtifactsResponseBody<CombatResponseBody> {
        return sendPostRequest("/my/$characterName/action/fight", "{}").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<CombatResponseBody>>(responseBody)
        }
    }
}