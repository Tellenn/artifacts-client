package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.models.Raid
import org.springframework.stereotype.Component

/**
 * Client for the Artifacts /raids endpoint.
 */
@Component
class RaidClient(deps: BaseClientDependencies) : BaseArtifactsClient(deps) {

    fun getRaids(
        name: String? = null,
        active: Boolean? = null,
        page: Int = 1,
        size: Int = 50,
    ): ArtifactsArrayResponseBody<Raid> {
        val queryParams = mutableMapOf<String, String>()
        if (name != null) queryParams["name"] = name
        if (active != null) queryParams["active"] = active.toString()
        queryParams["page"] = page.toString()
        queryParams["size"] = size.toString()

        val queryString = queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        val path = "/raids${if (queryString.isNotEmpty()) "?$queryString" else ""}"

        return sendGetRequest(path).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<Raid>>(responseBody)
        }
    }
}
