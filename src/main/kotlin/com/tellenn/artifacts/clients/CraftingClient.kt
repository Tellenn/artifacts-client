package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.models.RecycleRequest
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.CraftingResponseBody
import org.springframework.stereotype.Component

@Component
class CraftingClient(deps: BaseClientDependencies) : BaseArtifactsClient(deps) {

    fun craft(characterName: String, itemCode: String, quantity: Int = 1): ArtifactsResponseBody<CraftingResponseBody> {
        waitForCooldown(characterName)
        val request = SimpleItem(itemCode, quantity)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName/action/crafting", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<CraftingResponseBody>>(responseBody)
        }
    }

    fun recycle(characterName: String, itemCode: String, quantity: Int, enhanced: Boolean = false) :  ArtifactsResponseBody<CraftingResponseBody>  {
        waitForCooldown(characterName)
        val request = RecycleRequest(itemCode, quantity, enhanced)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName/action/recycling", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<CraftingResponseBody>>(responseBody)
        }
    }
}