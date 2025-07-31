package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias

@Suppress("unused")
class MonsterData(
    @JsonAlias("name") val name: String,
    @JsonAlias("code") val code: String,
    @JsonAlias("level") val level: Int,
    @JsonAlias("hp") val hp: Int,
    @JsonAlias("attack_fire") val attackFire: Int,
    @JsonAlias("attack_earth") val attackEarth: Int,
    @JsonAlias("attack_water") val attackWater: Int,
    @JsonAlias("attack_air") val attackAir: Int,
    @JsonAlias("res_fire") val defenseFire: Int,
    @JsonAlias("res_earth") val defenseEarth: Int,
    @JsonAlias("res_water") val defenseWater: Int,
    @JsonAlias("res_air") val defenseAir: Int,
    @JsonAlias("critical_strike") val criticalStrike: Int,
    @JsonAlias("effects") val effects: List<Effect>,
    @JsonAlias("min_gold") val minGold: Int,
    @JsonAlias("max_gold") val maxGold: Int,
    @JsonAlias("drops") val drops: List<MonsterDrop>?
)

class MonsterDrop(
    @JsonAlias("code") val code: String,
    @JsonAlias("max_quantity") val maxQuantity: Int,
    @JsonAlias("min_quantity") val minQuantity: Int,
    @JsonAlias("rate") val rate: Int
)

class MonsterEffect(
    @JsonAlias("code") val code: String,
    @JsonAlias("value") val value: Int
)