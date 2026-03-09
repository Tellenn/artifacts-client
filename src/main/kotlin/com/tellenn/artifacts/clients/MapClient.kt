package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class MapClient : BaseArtifactsClient() {

    fun getMaps(name: String? = null, 
                content_type: String? = null, content_code: String? = null,
                page: Int = 1, size: Int = 50): ArtifactsArrayResponseBody<MapData> {
        val queryParams = buildQueryParams(
            "name" to name,
            "content_type" to content_type,
            "content_code" to content_code,
            "page" to page.toString(),
            "size" to size.toString()
        )
        return sendGetRequest("/maps$queryParams").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<MapData>>(responseBody)
        }
    }

    private fun buildQueryParams(vararg params: Pair<String, String?>): String {
        val queryParams = params
            .filter { it.second != null }
            .joinToString("&") { "${it.first}=${it.second}" }

        return if (queryParams.isNotEmpty()) "?$queryParams" else ""
    }

}
