package com.tellenn.artifacts.clients.requests

import com.fasterxml.jackson.annotation.JsonProperty

class CreateCharacterRequest(
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("skin") val skin: String
)