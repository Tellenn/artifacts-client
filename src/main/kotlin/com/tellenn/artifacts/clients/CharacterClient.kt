package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.models.EquipmentSlot
import com.tellenn.artifacts.clients.models.SimpleItem
import com.tellenn.artifacts.clients.requests.EquipRequest
import com.tellenn.artifacts.clients.requests.UnequipRequest
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.EquipmentResponseBody
import com.tellenn.artifacts.clients.responses.RestResponseBody
import com.tellenn.artifacts.clients.responses.UseItemResponseBody
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class CharacterClient : BaseArtifactsClient() {

    fun rest(characterName: String): ArtifactsResponseBody<RestResponseBody> {
        return sendPostRequest("/my/$characterName/action/rest", "").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<RestResponseBody>>(responseBody)
        }
    }

    fun equipItem(characterName: String, itemCode: String, slot: String, quantity: Int): ArtifactsResponseBody<EquipmentResponseBody> {
        val request = EquipRequest(itemCode, slot, quantity)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName/action/equip", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<EquipmentResponseBody>>(responseBody)
        }
    }

    fun unequipItem(characterName: String, slot: EquipmentSlot): ArtifactsResponseBody<EquipmentResponseBody> {
        val request = UnequipRequest(slot.toString())
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName/action/unequip", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<EquipmentResponseBody>>(responseBody)
        }
    }

    fun useItem(characterName: String, itemCode: String, quantity: Int = 1): ArtifactsResponseBody<UseItemResponseBody> {
        val request = SimpleItem(itemCode, quantity)
        val requestBody = objectMapper.writeValueAsString(request)
        return sendPostRequest("/my/$characterName/action/use", requestBody).use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<UseItemResponseBody>>(responseBody)
        }
    }
}
