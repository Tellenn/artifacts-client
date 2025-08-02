package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonAlias

@Suppress("unused")
class BankDetails(
    @JsonAlias("gold") val gold: Int,
    @JsonAlias("next_expansion_cost") val nextExpansionCost: Int,
    @JsonAlias("expansions") val expansions: Int,
    @JsonAlias("slots") val slots: Int
)