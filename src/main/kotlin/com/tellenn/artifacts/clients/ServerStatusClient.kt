package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.models.ServerStatus
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import org.springframework.stereotype.Component

@Component
class ServerStatusClient(deps: BaseClientDependencies) : BaseArtifactsClient(deps) {

    fun getServerStatus() : ArtifactsResponseBody<ServerStatus> {
        return sendGetRequest("/").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<ServerStatus>>(responseBody)
        }
    }
}