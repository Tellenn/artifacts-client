package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class MonsterData(
    @JsonProperty("name") val name: String,
    @JsonProperty("code") val code: String,
    @JsonProperty("level") val level: Int,
    @JsonProperty("hp") val hp: Int,
    @JsonProperty("attack_fire") val attackFire: Int,
    @JsonProperty("attack_earth") val attackEarth: Int,
    @JsonProperty("attack_water") val attackWater: Int,
    @JsonProperty("attack_air") val attackAir: Int,
    @JsonProperty("res_fire") val defenseFire: Int,
    @JsonProperty("res_earth") val defenseEarth: Int,
    @JsonProperty("res_water") val defenseWater: Int,
    @JsonProperty("res_air") val defenseAir: Int,
    @JsonProperty("critical_strike") val criticalStrike: Int,
    @JsonProperty("effects") val effects: List<Effect>,
    @JsonProperty("min_gold") val minGold: Int,
    @JsonProperty("max_gold") val maxGold: Int,
    @JsonProperty("drops") val drops: List<MonsterDrop>?
)

class MonsterDrop(
    @JsonProperty("code") val code: String,
    @JsonProperty("max_quantity") val maxQuantity: Int,
    @JsonProperty("min_quantity") val minQuantity: Int,
    @JsonProperty("rate") val rate: Int
)

class MonsterEffect(
    @JsonProperty("code") val code: String,
    @JsonProperty("value") val value: Int
)