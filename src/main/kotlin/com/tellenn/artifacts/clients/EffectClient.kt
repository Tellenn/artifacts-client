package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.db.documents.EffectDocument
import org.springframework.stereotype.Component

@Component
class EffectClient(deps: BaseClientDependencies) : BaseArtifactsClient(deps) {

    fun getEffects(page: Int = 1, size: Int = 50): ArtifactsArrayResponseBody<EffectDocument> {
        return sendGetRequest("/effects?page=$page&size=$size").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<EffectDocument>>(responseBody)
        }
    }
}
