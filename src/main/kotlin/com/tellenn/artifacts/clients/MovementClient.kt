package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.MovementResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class MovementClient : BaseArtifactsClient() {

    fun move(characterName: String, mapId: Int): ArtifactsResponseBody<MovementResponseBody> {
        val response = sendPostRequest("/my/$characterName/action/move", "{\"mapId\": ${mapId}}")
        val movementResponse : ArtifactsResponseBody<MovementResponseBody> =
            objectMapper.readValue<ArtifactsResponseBody<MovementResponseBody>>(response.body!!.string())

        return movementResponse
    }
    fun transition(characterName: String): ArtifactsResponseBody<MovementResponseBody> {
        val response = sendPostRequest("/my/$characterName/action/transition", "{}")
        val movementResponse : ArtifactsResponseBody<MovementResponseBody> =
            objectMapper.readValue<ArtifactsResponseBody<MovementResponseBody>>(response.body!!.string())

        return movementResponse
    }
}
