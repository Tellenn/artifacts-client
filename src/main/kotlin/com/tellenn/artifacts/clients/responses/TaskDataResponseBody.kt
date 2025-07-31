package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown

class TaskDataResponseBody(
    @JsonAlias("cooldown") val cooldown: Cooldown,
    @JsonAlias("character") val character: ArtifactsCharacter,
    @JsonAlias("task") val task: Task
)

class Task(
    @JsonAlias("code") val items: String,
    @JsonAlias("type") val type: String,
    @JsonAlias("total") val total: String,
    @JsonAlias("rewards") val rewards: Rewards
)