package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.SimpleItem

class BankItemTransaction(
    @JsonAlias("cooldown") val cooldown: Cooldown,
    @JsonAlias("character") val character: ArtifactsCharacter,
    @JsonAlias("items") val items: List<SimpleItem>,
    @JsonAlias("bank") val bank: List<SimpleItem>
)
