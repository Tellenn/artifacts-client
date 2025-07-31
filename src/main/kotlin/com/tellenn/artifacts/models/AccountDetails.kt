package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonAlias
import java.time.Instant

@Suppress("unused")
class AccountDetails(
    @JsonAlias("username") val username: String,
    @JsonAlias("email") val email: String,
    @JsonAlias("created_at") val createdAt: Instant,
    @JsonAlias("characters") val characters: List<String>,
    @JsonAlias("max_characters") val maxCharacters: Int
)