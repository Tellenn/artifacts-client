package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class MonsterData(
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("level") val level: Int,
    @param:JsonProperty("hp") val hp: Int,
    @param:JsonProperty("attack_fire") val attackFire: Int,
    @param:JsonProperty("attack_earth") val attackEarth: Int,
    @param:JsonProperty("attack_water") val attackWater: Int,
    @param:JsonProperty("attack_air") val attackAir: Int,
    @param:JsonProperty("res_fire") val defenseFire: Int,
    @param:JsonProperty("res_earth") val defenseEarth: Int,
    @param:JsonProperty("res_water") val defenseWater: Int,
    @param:JsonProperty("res_air") val defenseAir: Int,
    @param:JsonProperty("critical_strike") val criticalStrike: Int,
    @param:JsonProperty("effects") val effects: List<Effect>,
    @param:JsonProperty("min_gold") val minGold: Int,
    @param:JsonProperty("max_gold") val maxGold: Int,
    @param:JsonProperty("drops") val drops: List<MonsterDrop>?,
    @param:JsonProperty("initiative") val initiative: Int,
    @param:JsonProperty("type") val type: String?
)

@Suppress("unused")
class MonsterDrop(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("max_quantity") val maxQuantity: Int,
    @param:JsonProperty("min_quantity") val minQuantity: Int,
    @param:JsonProperty("rate") val rate: Int
)