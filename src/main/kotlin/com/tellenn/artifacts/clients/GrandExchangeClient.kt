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

    /** Achète [quantity] unités de l'ordre de vente [orderId] — l'API achète par id d'ordre. */
    fun buyItem(characterName: String, orderId: String, quantity: Int): ArtifactsResponseBody<GEBuyTransaction> {
        return sendPostRequest(
            "/my/$characterName/action/grandexchange/buy",
            """{"id": "$orderId", "quantity": $quantity}"""
        ).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<GEBuyTransaction>>(responseBody)
        }
    }

    /** Crée un ordre de vente au Grand Exchange (le personnage doit y être, items en inventaire). */
    fun createSellOrder(characterName: String, itemCode: String, quantity: Int, price: Int): ArtifactsResponseBody<GEBuyTransaction> {
        return sendPostRequest(
            "/my/$characterName/action/grandexchange/create_sell_order",
            """{"code": "$itemCode", "quantity": $quantity, "price": $price}"""
        ).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<GEBuyTransaction>>(responseBody)
        }
    }

    /** Annule un de nos ordres — les items invendus reviennent dans l'inventaire du personnage. */
    fun cancelOrder(characterName: String, orderId: String): ArtifactsResponseBody<GEBuyTransaction> {
        return sendPostRequest(
            "/my/$characterName/action/grandexchange/cancel",
            """{"id": "$orderId"}"""
        ).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<GEBuyTransaction>>(responseBody)
        }
    }
}
