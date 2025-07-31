package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.DataResponseBody
import com.tellenn.artifacts.clients.responses.RewardDataResponseBody
import com.tellenn.artifacts.clients.responses.TaskDataResponseBody
import com.tellenn.artifacts.clients.responses.TaskTradeResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class TaskClient : BaseArtifactsClient() {

    fun giveItem(name: String, itemCode: String, quantity: Int = 1): ArtifactsResponseBody<TaskTradeResponseBody> {
        val request = SimpleItem(itemCode, quantity)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$name/action/task/trade", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<TaskTradeResponseBody>>(responseBody)
        }
    }

    fun completeTask(name: String): ArtifactsResponseBody<RewardDataResponseBody> {
        return sendPostRequest("/my/$name/action/task/complete", "").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<RewardDataResponseBody>>(responseBody)
        }
    }

    fun acceptTask(name: String) : ArtifactsResponseBody<TaskDataResponseBody> {
        return sendPostRequest("/my/$name/action/task/new", "").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<TaskDataResponseBody>>(responseBody)
        }
    }

    fun abandonTask(name: String) : ArtifactsResponseBody<DataResponseBody> {
        return sendPostRequest("/my/$name/action/task/cancel", "").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<DataResponseBody>>(responseBody)
        }
    }
}