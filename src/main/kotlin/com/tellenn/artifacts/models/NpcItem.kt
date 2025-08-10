package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

class NpcItem(
    @JsonProperty("code") val code: String,
    @JsonProperty("npc") val npc: String,
    @JsonProperty("currency") val currency: String,
    @JsonProperty("buy_price") val buyPrice: Int?,
    @JsonProperty("sell_price") val sellPrice: Int?,

    ){
}