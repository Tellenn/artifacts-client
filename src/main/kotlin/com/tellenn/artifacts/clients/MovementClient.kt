package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.MovementResponseBody
import lombok.extern.slf4j.Slf4j

@Slf4j
class MovementClient(val character: String) : BaseArtifactsClient() {
    /* Body :
    {
        "x": 0,
        "y": 0
    }*/
    fun moveCharacterToCell(cellCode: String) : ArtifactsResponseBody<MovementResponseBody> {
        var response = sendPostRequest("/my/$character/action/move", cellCode)
        var movementResponse : ArtifactsResponseBody<MovementResponseBody> =
            objectMapper.readValue<ArtifactsResponseBody<MovementResponseBody>>(response.body!!.string())

        return movementResponse;
    }
}