package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.GEBuyTransaction
import com.tellenn.artifacts.models.GEOrder
import org.springframework.stereotype.Component

@Component
class GrandExchangeClient(deps: BaseClientDependencies) : BaseArtifactsClient(deps) {

    fun getPublicSellOrders(itemCode: String): ArtifactsArrayResponseBody<GEOrder> {
        return sendGetRequest("/grandexchange/orders?item_code=$itemCode").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<GEOrder>>(responseBody)
        }
    }

    fun buyItem(characterName: String, itemCode: String, quantity: Int, price: Int): ArtifactsResponseBody<GEBuyTransaction> {
        return sendPostRequest(
            "/my/$characterName/action/grandexchange/buy",
            """{"code": "$itemCode", "quantity": $quantity, "price": $price}"""
        ).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<GEBuyTransaction>>(responseBody)
        }
    }
}
