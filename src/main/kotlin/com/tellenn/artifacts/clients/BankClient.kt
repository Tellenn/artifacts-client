package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.models.ItemDetails
import com.tellenn.artifacts.clients.models.SimpleItem
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class BankClient : BaseArtifactsClient() {

    fun getBankedItems(): ArtifactsArrayResponseBody<SimpleItem> {
        return sendGetRequest("/my/bank/items").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<SimpleItem>>(responseBody)
        }
    }

}
