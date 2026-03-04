package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class BankDetails(
    @param:JsonProperty("gold") val gold: Int,
    @param:JsonProperty("next_expansion_cost") val nextExpansionCost: Int,
    @param:JsonProperty("expansions") val expansions: Int,
    @param:JsonProperty("slots") val slots: Int
)