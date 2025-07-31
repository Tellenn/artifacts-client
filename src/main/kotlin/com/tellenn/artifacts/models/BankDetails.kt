package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonAlias

@Suppress("unused")
class BankDetails(
    @JsonAlias("gold") val gold: Int,
    @JsonAlias("items") val items: Int,
    @JsonAlias("max_items") val maxItems: Int
)