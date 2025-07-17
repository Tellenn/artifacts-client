package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.models.SimpleItem
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.CraftingResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class TaskClient : BaseArtifactsClient() {

    fun craft(characterName: String, itemCode: String, quantity: Int = 1): ArtifactsResponseBody<CraftingResponseBody> {
        val request = SimpleItem(itemCode, quantity)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName/action/craft", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<CraftingResponseBody>>(responseBody)
        }
    }

}