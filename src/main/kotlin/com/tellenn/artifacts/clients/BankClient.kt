package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.SimpleItem
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.BankItemTransaction
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

    fun depositItems(characterName: String, items : List<SimpleItem>){
        return sendPostRequest("/my/$characterName/action/bank/deposit/item",  objectMapper.writeValueAsString(items)).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<BankItemTransaction>>(responseBody)
        }
    }

}
