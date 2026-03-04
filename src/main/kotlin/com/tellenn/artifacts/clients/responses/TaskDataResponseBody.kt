package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown

@Suppress("unused")
class TaskDataResponseBody(
    @param:JsonProperty("cooldown") val cooldown: Cooldown,
    @param:JsonProperty("character") val character: ArtifactsCharacter,
    @param:JsonProperty("task") val task: Task
)

@Suppress("unused")
class Task(
    @param:JsonProperty("code") val items: String,
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("total") val total: String,
    @param:JsonProperty("rewards") val rewards: Rewards
)