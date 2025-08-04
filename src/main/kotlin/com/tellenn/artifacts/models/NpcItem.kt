package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonAlias

class NpcItem(
    @JsonAlias("code") val code: String,
    @JsonAlias("npc") val npc: String,
    @JsonAlias("currency") val currency: String,
    @JsonAlias("buy_price") val buyPrice: Int?,
    @JsonAlias("sell_price") val sellPrice: Int?,

    ){
}