package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class Achievement(
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("description") val description: String,
    @param:JsonProperty("points") val points: Int,
    @param:JsonProperty("objectives") val objectives: List<Objective>,
    @param:JsonProperty("rewards") val rewards: List<Reward>,
    @param:JsonProperty("completed_at") val completedAt: String?
    )

@Suppress("unused")
class Objective(
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("target") val target: String?,
    @param:JsonProperty("progress") val progress: Int?,
    @param:JsonProperty("total") val total: Int,
)

@Suppress("unused")
class Reward(
    @param:JsonProperty("gold") val type: String,
    @param:JsonProperty("items") val quantity: List<SimpleItem>,
)