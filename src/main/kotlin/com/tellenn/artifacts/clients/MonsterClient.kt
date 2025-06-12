package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.models.MonsterData
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class MonsterClient : BaseArtifactsClient() {

    fun getMonsters(name: String? = null, level: Int? = null, 
                    min_level: Int? = null, max_level: Int? = null, drop: String? = null,
                    page: Int = 1, size: Int = 50): ArtifactsArrayResponseBody<MonsterData> {
        val queryParams = buildQueryParams(
            "name" to name,
            "level" to level?.toString(),
            "min_level" to min_level?.toString(),
            "max_level" to max_level?.toString(),
            "drop" to drop,
            "page" to page.toString(),
            "size" to size.toString()
        )
        return sendGetRequest("/monsters$queryParams").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<MonsterData>>(responseBody)
        }
    }

    fun getMonster(monsterId: String): ArtifactsResponseBody<MonsterData> {
        return sendGetRequest("/monsters/$monsterId").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<MonsterData>>(responseBody)
        }
    }

    private fun buildQueryParams(vararg params: Pair<String, String?>): String {
        val queryParams = params
            .filter { it.second != null }
            .joinToString("&") { "${it.first}=${it.second}" }

        return if (queryParams.isNotEmpty()) "?$queryParams" else ""
    }
}
