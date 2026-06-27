package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.MovementResponseBody
import com.tellenn.artifacts.clients.responses.TransitionResponseBody
import org.springframework.stereotype.Component

@Component
class MovementClient(deps: BaseClientDependencies) : BaseArtifactsClient(deps) {

    fun move(characterName: String, mapId: Int): ArtifactsResponseBody<MovementResponseBody> {
        waitForCooldown(characterName)
        val response = sendPostRequest("/my/$characterName/action/move", "{\"map_id\": ${mapId}}")
        val movementResponse : ArtifactsResponseBody<MovementResponseBody> =
            objectMapper.readValue<ArtifactsResponseBody<MovementResponseBody>>(response.body!!.string())

        return movementResponse
    }
    fun transition(characterName: String): ArtifactsResponseBody<TransitionResponseBody> {
        waitForCooldown(characterName)
        val response = sendPostRequest("/my/$characterName/action/transition", "{}")
        val movementResponse : ArtifactsResponseBody<TransitionResponseBody> =
            objectMapper.readValue<ArtifactsResponseBody<TransitionResponseBody>>(response.body!!.string())

        return movementResponse
    }
}
