package com.tellenn.artifacts.clients.requests

import com.fasterxml.jackson.annotation.JsonProperty

class CreateCharacterRequest(
    @JsonProperty("name") val name: String,
    @JsonProperty("skin") val skin: String
)