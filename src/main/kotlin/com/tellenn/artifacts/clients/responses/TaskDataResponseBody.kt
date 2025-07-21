package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.Cooldown
import com.tellenn.artifacts.clients.models.SimpleItem

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