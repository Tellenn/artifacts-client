package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.BankExtensionTransaction
import com.tellenn.artifacts.clients.responses.BankGoldTransaction
import com.tellenn.artifacts.clients.responses.BankItemTransaction
import com.tellenn.artifacts.models.BankDetails
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class BankClient : BaseArtifactsClient() {

    fun getBankedItems(itemCode: String? = null): ArtifactsArrayResponseBody<SimpleItem> {
        val path = if (itemCode != null) {
            "/my/bank/items?item_code=$itemCode"
        } else {
            "/my/bank/items"
        }

        return sendGetRequest(path).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<SimpleItem>>(responseBody)
        }
    }

    fun depositItems(characterName: String, items : List<SimpleItem>) : ArtifactsResponseBody<BankItemTransaction>{
        return sendPostRequest("/my/$characterName/action/bank/deposit/item",  objectMapper.writeValueAsString(items)).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<BankItemTransaction>>(responseBody)
        }
    }

    fun withdrawItems(characterName: String, items : List<SimpleItem>) : ArtifactsResponseBody<BankItemTransaction>{
        return sendPostRequest("/my/$characterName/action/bank/withdraw/item",  objectMapper.writeValueAsString(items)).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<BankItemTransaction>>(responseBody)
        }
    }

    fun depositGold(characterName: String, amount: Int)  : ArtifactsResponseBody<BankGoldTransaction> {
        return sendPostRequest("/my/$characterName/action/bank/deposit/gold",  "{\"quantity\":"+ amount +"}").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<BankGoldTransaction>>(responseBody)
        }
    }

    fun withdrawGold(characterName: String, amount: Int)  : ArtifactsResponseBody<BankGoldTransaction> {
        return sendPostRequest("/my/$characterName/action/bank/withdraw/gold",  "{\"quantity\":"+ amount +"}").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<BankGoldTransaction>>(responseBody)
        }
    }

    fun getBankDetails(): ArtifactsResponseBody<BankDetails> {
        return sendGetRequest("/my/bank").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<BankDetails>>(responseBody)
        }
    }

    fun buyBankExpansion(characterName: String) : ArtifactsResponseBody<BankExtensionTransaction> {
        return sendPostRequest("/my/$characterName/action/bank/buy_expansion",  "").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<BankExtensionTransaction>>(responseBody)
        }
    }

}
