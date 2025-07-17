package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.Cooldown
import com.tellenn.artifacts.clients.models.SimpleItem

class BankItemTransaction(
    @JsonAlias("cooldown") val cooldown: Cooldown,
    @JsonAlias("character") val character: ArtifactsCharacter,
    @JsonAlias("items") val items: List<SimpleItem>,
    @JsonAlias("bank") val bank: List<SimpleItem>
)
