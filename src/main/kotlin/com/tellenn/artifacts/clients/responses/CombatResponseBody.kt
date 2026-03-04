package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.SimpleItem

@Suppress("unused")
class CombatResponseBody(
    @param:JsonProperty("cooldown") val cooldown: Cooldown,
    @param:JsonProperty("characters") val characters: List<ArtifactsCharacter>,
    @param:JsonProperty("fight") val fight: Fight?
)

@Suppress("unused")
class Fight(
    @param:JsonProperty("turns") val turns: Int,
    @param:JsonProperty("logs") val logs: List<String>,
    @param:JsonProperty("result") val result: String,
    @param:JsonProperty("opponent") val opponentCode: String,
    @param:JsonProperty("characters") val characters: List<CharacterResult>
)

@Suppress("unused")
class CharacterResult(
    @param:JsonProperty("character_name") val characterName: String,
    @param:JsonProperty("xp") val xp: Int,
    @param:JsonProperty("gold") val gold: Int,
    @param:JsonProperty("drops") val drops: List<SimpleItem>,
    @param:JsonProperty("final_hp") val finalHp: Int,
)