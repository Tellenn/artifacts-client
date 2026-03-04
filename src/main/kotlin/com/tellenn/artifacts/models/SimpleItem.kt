package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class SimpleItem(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("quantity") val quantity: Int = 1
)