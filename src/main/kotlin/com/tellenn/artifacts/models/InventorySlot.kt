package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

class InventorySlot(
    @param:JsonProperty("slot") val slot : Int,
    @param:JsonProperty("code") val code : String,
    @param:JsonProperty("quantity") val quantity : Int
) {
}