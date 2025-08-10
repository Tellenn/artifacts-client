package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown

class TaskDataResponseBody(
    @JsonProperty("cooldown") val cooldown: Cooldown,
    @JsonProperty("character") val character: ArtifactsCharacter,
    @JsonProperty("task") val task: Task
)

class Task(
    @JsonProperty("code") val items: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("total") val total: String,
    @JsonProperty("rewards") val rewards: Rewards
)