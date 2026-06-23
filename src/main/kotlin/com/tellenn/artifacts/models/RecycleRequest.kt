package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class RecycleRequest(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("quantity") val quantity: Int = 1,
    @param:JsonProperty("enhanced") val enhanced: Boolean = false,
)