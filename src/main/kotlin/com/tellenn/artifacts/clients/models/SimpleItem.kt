package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class SimpleItem(
    @JsonProperty("code") val code: String,
    @JsonProperty("quantity") val quantity: Int = 1
)