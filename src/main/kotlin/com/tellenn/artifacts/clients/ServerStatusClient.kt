package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.models.ServerStatus
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class ServerStatusClient() : BaseArtifactsClient() {

    fun getServerStatus() : ArtifactsResponseBody<ServerStatus> {
        return sendGetRequest("/").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<ServerStatus>>(responseBody)
        }
    }
}