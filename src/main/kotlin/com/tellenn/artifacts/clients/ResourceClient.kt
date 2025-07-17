package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.models.Resource
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import org.springframework.stereotype.Component

/**
 * Client for interacting with the resource-related endpoints of the Artifacts API.
 * Provides methods to fetch resources.
 */
@Component
class ResourceClient : BaseArtifactsClient() {

    /**
     * Fetches resources from the API with optional filtering and pagination.
     *
     * @param skill Optional filter for resources by skill type (e.g., "mining", "woodcutting")
     * @param level Optional filter for resources by minimum level requirement
     * @param page Page number for pagination
     * @param size Number of items per page
     * @return A paginated response containing resources
     */
    fun getResources(
        skill: String? = null,
        level: Int? = null,
        page: Int = 1,
        size: Int = 50
    ): ArtifactsArrayResponseBody<Resource> {
        val queryParams = mutableMapOf<String, String>()
        
        if (skill != null) {
            queryParams["skill"] = skill
        }
        
        if (level != null) {
            queryParams["level"] = level.toString()
        }
        
        queryParams["page"] = page.toString()
        queryParams["size"] = size.toString()
        
        val queryString = queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        val url = "/resources${if (queryString.isNotEmpty()) "?$queryString" else ""}"
        
        return sendGetRequest(url).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<Resource>>(responseBody)
        }
    }
}