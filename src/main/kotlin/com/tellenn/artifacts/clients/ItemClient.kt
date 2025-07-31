package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class ItemClient : BaseArtifactsClient() {

    fun getItems(name: String? = null, type: String? = null, rarity: String? = null, level: Int? = null, 
                 equippable: Boolean? = null, usable: Boolean? = null, stackable: Boolean? = null,
                 slot: String? = null, page: Int = 1, size: Int = 50): ArtifactsArrayResponseBody<ItemDetails> {
        val queryParams = buildQueryParams(
            "name" to name,
            "type" to type,
            "rarity" to rarity,
            "level" to level?.toString(),
            "equippable" to equippable?.toString(),
            "usable" to usable?.toString(),
            "stackable" to stackable?.toString(),
            "slot" to slot,
            "page" to page.toString(),
            "size" to size.toString()
        )
        return sendGetRequest("/items$queryParams").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<ItemDetails>>(responseBody)
        }
    }

    fun getItemDetails(itemCode: String): ArtifactsResponseBody<ItemDetails> {
        return sendGetRequest("/items/$itemCode").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<ItemDetails>>(responseBody)
        }
    }

    private fun buildQueryParams(vararg params: Pair<String, String?>): String {
        val queryParams = params
            .filter { it.second != null }
            .joinToString("&") { "${it.first}=${it.second}" }

        return if (queryParams.isNotEmpty()) "?$queryParams" else ""
    }
}
