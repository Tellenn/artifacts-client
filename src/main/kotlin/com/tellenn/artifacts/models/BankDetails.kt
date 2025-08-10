package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class BankDetails(
    @JsonProperty("gold") val gold: Int,
    @JsonProperty("next_expansion_cost") val nextExpansionCost: Int,
    @JsonProperty("expansions") val expansions: Int,
    @JsonProperty("slots") val slots: Int
)