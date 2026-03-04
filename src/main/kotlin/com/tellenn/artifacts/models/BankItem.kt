package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class BankItem(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("quantity") val quantity: Int,
    @param:JsonProperty("description") val description: String?,
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("rarity") val rarity: String,
    @param:JsonProperty("level") val level: Int
)