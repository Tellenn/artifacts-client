package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Date

class InventorySlot(
    @JsonProperty("slot") val slot : Int,
    @JsonProperty("code") val code : String,
    @JsonProperty("quantity") val quantity : Int
) {
}