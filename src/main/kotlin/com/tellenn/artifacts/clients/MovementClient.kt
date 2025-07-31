package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.MovementResponseBody
import lombok.extern.slf4j.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Slf4j
@Component
class MovementClient : BaseArtifactsClient() {
    private val logger = LoggerFactory.getLogger(MovementClient::class.java)

    /* Body :
    {
        "x": 0,
        "y": 0
    }*/
    fun moveCharacterToCell(characterName: String, x: Int, y: Int) : ArtifactsResponseBody<MovementResponseBody> {
        return makeMovementCall(characterName, x, y)
    }

    fun makeMovementCall(characterName: String, x: Int, y: Int): ArtifactsResponseBody<MovementResponseBody> {
        var response = sendPostRequest("/my/$characterName/action/move", "{\"x\": ${x}, \"y\": ${y}}")
        var movementResponse : ArtifactsResponseBody<MovementResponseBody> =
            objectMapper.readValue<ArtifactsResponseBody<MovementResponseBody>>(response.body!!.string())

        return movementResponse
    }
}
