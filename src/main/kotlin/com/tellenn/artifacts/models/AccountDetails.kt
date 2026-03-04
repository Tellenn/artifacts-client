package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@Suppress("unused")
class AccountDetails(
    @param:JsonProperty("username") val username: String,
    @param:JsonProperty("email") val email: String,
    @param:JsonProperty("created_at") val createdAt: Instant,
    @param:JsonProperty("characters") val characters: List<String>,
    @param:JsonProperty("max_characters") val maxCharacters: Int
)