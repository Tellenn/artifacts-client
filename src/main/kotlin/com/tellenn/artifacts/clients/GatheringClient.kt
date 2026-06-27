package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.GatheringResponseBody
import org.springframework.stereotype.Component

@Component
class GatheringClient(deps: BaseClientDependencies) : BaseArtifactsClient(deps) {

    fun gather(characterName: String): ArtifactsResponseBody<GatheringResponseBody> {
        waitForCooldown(characterName)
        return sendPostRequest("/my/$characterName/action/gathering", "").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<GatheringResponseBody>>(responseBody)
        }
    }
}