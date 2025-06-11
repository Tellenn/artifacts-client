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

    fun gather(characterName: String, resourceCode: String): ArtifactsResponseBody<GatheringResponseBody> {
        val request = SimpleItem(resourceCode)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName/action/gather", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<GatheringResponseBody>>(responseBody)
        }
    }

    fun mine(characterName: String, resourceCode: String): ArtifactsResponseBody<GatheringResponseBody> {
        val request = SimpleItem(resourceCode)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName/action/mine", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<GatheringResponseBody>>(responseBody)
        }
    }

    fun fish(characterName: String, resourceCode: String): ArtifactsResponseBody<GatheringResponseBody> {
        val request = SimpleItem(resourceCode)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName/action/fish", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<GatheringResponseBody>>(responseBody)
        }
    }

    fun chop(characterName: String, resourceCode: String): ArtifactsResponseBody<GatheringResponseBody> {
        val request = SimpleItem(resourceCode)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName/action/chop", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<GatheringResponseBody>>(responseBody)
        }
    }
}