package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class BankItem(
    @JsonProperty("code") val code: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("quantity") val quantity: Int,
    @JsonProperty("description") val description: String?,
    @JsonProperty("type") val type: String,
    @JsonProperty("rarity") val rarity: String,
    @JsonProperty("level") val level: Int
)