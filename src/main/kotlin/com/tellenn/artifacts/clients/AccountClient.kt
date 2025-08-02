package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.models.AccountDetails
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.GEOrder
import com.tellenn.artifacts.models.GEOrderHistory
import com.tellenn.artifacts.clients.requests.CreateCharacterRequest
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Service

@Slf4j
@Service
class AccountClient() : BaseArtifactsClient() {

    fun getGESellOrders(code: String? = null, page: Int = 1, size: Int = 50): ArtifactsArrayResponseBody<GEOrder> {
        val queryParams = buildQueryParams(
            "code" to code,
            "page" to page.toString(),
            "size" to size.toString()
        )
        return sendGetRequest("/my/grandexchange/orders$queryParams").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<GEOrder>>(responseBody)
        }
    }

    fun getGESellHistory(id: String? = null, code: String? = null, page: Int = 1, size: Int = 50): ArtifactsArrayResponseBody<GEOrderHistory> {
        val queryParams = buildQueryParams(
            "id" to id,
            "code" to code,
            "page" to page.toString(),
            "size" to size.toString()
        )
        return sendGetRequest("/my/grandexchange/history$queryParams").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<GEOrderHistory>>(responseBody)
        }
    }

    fun getAccountDetails(): ArtifactsResponseBody<AccountDetails> {
        return sendGetRequest("/my/details").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<AccountDetails>>(responseBody)
        }
    }

    private fun buildQueryParams(vararg params: Pair<String, String?>): String {
        val queryParams = params
            .filter { it.second != null }
            .joinToString("&") { "${it.first}=${it.second}" }

        return if (queryParams.isNotEmpty()) "?$queryParams" else ""
    }

    fun createCharacter(name: String, skin: String): ArtifactsResponseBody<ArtifactsCharacter> {
        val request = CreateCharacterRequest(name, skin)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/characters/create", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<ArtifactsCharacter>>(responseBody)
        }
    }

    fun getCharacter(name: String): ArtifactsResponseBody<ArtifactsCharacter> {
        return sendGetRequest("/characters/$name").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<ArtifactsCharacter>>(responseBody)
        }
    }
}
