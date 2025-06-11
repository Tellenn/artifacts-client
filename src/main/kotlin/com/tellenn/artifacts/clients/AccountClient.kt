package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.models.BankDetails
import com.tellenn.artifacts.clients.models.AccountDetails
import com.tellenn.artifacts.clients.models.BankItem
import com.tellenn.artifacts.clients.models.GEOrder
import com.tellenn.artifacts.clients.models.GEOrderHistory
import com.tellenn.artifacts.clients.requests.ChangePasswordRequest
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.DataPage
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class AccountClient : BaseArtifactsClient() {

    fun getBankDetails(): ArtifactsResponseBody<BankDetails> {
        return sendGetRequest("/my/bank").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<BankDetails>>(responseBody)
        }
    }

    fun getBankItems(itemCode: String? = null, page: Int = 1, size: Int = 50): ArtifactsResponseBody<DataPage<BankItem>> {
        val queryParams = buildQueryParams(
            "item_code" to itemCode,
            "page" to page.toString(),
            "size" to size.toString()
        )
        return sendGetRequest("/my/bank/items$queryParams").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<DataPage<BankItem>>>(responseBody)
        }
    }

    fun getGESellOrders(code: String? = null, page: Int = 1, size: Int = 50): ArtifactsResponseBody<DataPage<GEOrder>> {
        val queryParams = buildQueryParams(
            "code" to code,
            "page" to page.toString(),
            "size" to size.toString()
        )
        return sendGetRequest("/my/grandexchange/orders$queryParams").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<DataPage<GEOrder>>>(responseBody)
        }
    }

    fun getGESellHistory(id: String? = null, code: String? = null, page: Int = 1, size: Int = 50): ArtifactsResponseBody<DataPage<GEOrderHistory>> {
        val queryParams = buildQueryParams(
            "id" to id,
            "code" to code,
            "page" to page.toString(),
            "size" to size.toString()
        )
        return sendGetRequest("/my/grandexchange/history$queryParams").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<DataPage<GEOrderHistory>>>(responseBody)
        }
    }

    fun getAccountDetails(): ArtifactsResponseBody<AccountDetails> {
        return sendGetRequest("/my/details").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<AccountDetails>>(responseBody)
        }
    }

    fun changePassword(oldPassword: String, newPassword: String): ArtifactsResponseBody<Any> {
        val request = ChangePasswordRequest(oldPassword, newPassword)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/change_password", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<Any>>(responseBody)
        }
    }

    private fun buildQueryParams(vararg params: Pair<String, String?>): String {
        val queryParams = params
            .filter { it.second != null }
            .joinToString("&") { "${it.first}=${it.second}" }
        
        return if (queryParams.isNotEmpty()) "?$queryParams" else ""
    }
}