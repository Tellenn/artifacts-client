package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.NpcMerchantTransaction
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.NpcItem
import org.springframework.stereotype.Component

@Component
class NpcClient(deps: BaseClientDependencies) : BaseArtifactsClient(deps) {

    fun getNpcItems(npcCode: String): ArtifactsArrayResponseBody<NpcItem> {
        return sendGetRequest("/npcs/items?npc=$npcCode").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<NpcItem>>(responseBody)
        }
    }

    /** Tous les items proposés par les NPC, toutes pages confondues. */
    fun getAllNpcItems(): List<NpcItem> {
        val all = mutableListOf<NpcItem>()
        var page = 1
        var pages = 1
        while (page <= pages) {
            val response = getNpcItemsPage(page, 100)
            all += response.data
            pages = response.pages
            page++
        }
        return all
    }

    private fun getNpcItemsPage(page: Int, size: Int): ArtifactsArrayResponseBody<NpcItem> {
        return sendGetRequest("/npcs/items?page=$page&size=$size").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<NpcItem>>(responseBody)
        }
    }
    fun getItemsBoughtWith(itemCode: String): ArtifactsArrayResponseBody<NpcItem> {
        return sendGetRequest("/npcs/items?currency=$itemCode").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<NpcItem>>(responseBody)
        }
    }

    fun sellItem(character: String, code: String, quantity: Int): ArtifactsResponseBody<NpcMerchantTransaction> {
        return sendPostRequest("/my/$character/action/npc/sell", "{\"code\": \"$code\", \"quantity\": $quantity}").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<NpcMerchantTransaction>>(responseBody)
        }
    }

    fun getNpcByItemCode(code: String): ArtifactsArrayResponseBody<NpcItem> {
        return sendGetRequest("/npcs/items?code=$code").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsArrayResponseBody<NpcItem>>(responseBody)
        }
    }

    fun buyItem(character: String, code: String, quantity: Int) : ArtifactsResponseBody<NpcMerchantTransaction> {
        return sendPostRequest("/my/$character/action/npc/buy", "{\"code\": \"$code\", \"quantity\": $quantity}").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<NpcMerchantTransaction>>(responseBody)
        }
    }
}
