package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.SimpleItem
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.CraftingResponseBody
import com.tellenn.artifacts.clients.responses.RewardDataResponseBody
import com.tellenn.artifacts.clients.responses.TaskTradeResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class TaskClient : BaseArtifactsClient() {

    fun giveItem(characterName: String, itemCode: String, quantity: Int = 1): ArtifactsResponseBody<TaskTradeResponseBody> {
        val request = SimpleItem(itemCode, quantity)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName/action/task/trade", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<TaskTradeResponseBody>>(responseBody)
        }
    }

    fun completeTask(characterName: String): ArtifactsResponseBody<RewardDataResponseBody> {
        return sendPostRequest("/my/$characterName/action/task/complete", "").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<RewardDataResponseBody>>(responseBody)
        }
    }

}