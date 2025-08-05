package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.NpcMerchantTransaction
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.NpcItem
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class NpcClient : BaseArtifactsClient() {

    fun getNpcItems(npcCode: String): ArtifactsResponseBody<List<NpcItem>> {
        return sendGetRequest("/npcs/details/$npcCode").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<List<NpcItem>>>(responseBody)
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
