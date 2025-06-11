package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.models.SimpleItem
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.CombatResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class CombatClient : BaseArtifactsClient() {

    fun attack(characterName: String, targetCode: String): ArtifactsResponseBody<CombatResponseBody> {
        val request = SimpleItem(targetCode)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName/action/attack", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<CombatResponseBody>>(responseBody)
        }
    }
}