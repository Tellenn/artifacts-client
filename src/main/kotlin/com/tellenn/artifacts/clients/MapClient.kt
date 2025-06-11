package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.models.MapData
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class MapClient : BaseArtifactsClient() {

    fun getMap(x: Int, y: Int, width: Int = 1, height: Int = 1): ArtifactsResponseBody<MapData> {
        val queryParams = buildQueryParams(
            "x" to x.toString(),
            "y" to y.toString(),
            "width" to width.toString(),
            "height" to height.toString()
        )
        return sendGetRequest("/map$queryParams").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<MapData>>(responseBody)
        }
    }

    private fun buildQueryParams(vararg params: Pair<String, String?>): String {
        val queryParams = params
            .filter { it.second != null }
            .joinToString("&") { "${it.first}=${it.second}" }
        
        return if (queryParams.isNotEmpty()) "?$queryParams" else ""
    }
}