package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.models.EventData
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class EventClient : BaseArtifactsClient() {

    fun getEvents(type: String? = null, page: Int = 1, size: Int = 50): ArtifactsArrayResponseBody<EventData> {
        val queryParams = buildQueryParams(
            "type" to type,
            "page" to page.toString(),
            "size" to size.toString()
        )
        return sendGetRequest("/events$queryParams").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<EventData>>(responseBody)
        }
    }

    private fun buildQueryParams(vararg params: Pair<String, String?>): String {
        val queryParams = params
            .filter { it.second != null }
            .joinToString("&") { "${it.first}=${it.second}" }

        return if (queryParams.isNotEmpty()) "?$queryParams" else ""
    }
}
