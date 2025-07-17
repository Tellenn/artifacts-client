package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.models.SimpleItem
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.GatheringResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class GatheringClient : BaseArtifactsClient() {

    fun gather(characterName: String): ArtifactsResponseBody<GatheringResponseBody> {
        return sendPostRequest("/my/$characterName/action/gathering", "").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<GatheringResponseBody>>(responseBody)
        }
    }
}