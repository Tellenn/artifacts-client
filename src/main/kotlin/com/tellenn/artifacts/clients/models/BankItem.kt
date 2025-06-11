package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias

@Suppress("unused")
class BankItem(
    @JsonAlias("code") val code: String,
    @JsonAlias("name") val name: String,
    @JsonAlias("quantity") val quantity: Int,
    @JsonAlias("description") val description: String?,
    @JsonAlias("type") val type: String,
    @JsonAlias("rarity") val rarity: String,
    @JsonAlias("level") val level: Int
)