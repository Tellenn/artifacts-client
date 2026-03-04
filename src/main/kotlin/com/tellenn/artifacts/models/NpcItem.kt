package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

class NpcItem(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("npc") val npc: String,
    @param:JsonProperty("currency") val currency: String,
    @param:JsonProperty("buy_price") val buyPrice: Int?,
    @param:JsonProperty("sell_price") val sellPrice: Int?,

    ){
}