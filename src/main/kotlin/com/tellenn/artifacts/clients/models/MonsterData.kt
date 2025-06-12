package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias

@Suppress("unused")
class MonsterData(
    @JsonAlias("id") val id: String,
    @JsonAlias("name") val name: String,
    @JsonAlias("level") val level: Int,
    @JsonAlias("hp") val hp: Int,
    @JsonAlias("attack") val attack: Int,
    @JsonAlias("defense") val defense: Int,
    @JsonAlias("speed") val speed: Int,
    @JsonAlias("xpReward") val xpReward: Int,
    @JsonAlias("goldReward") val goldReward: Int,
    @JsonAlias("drops") val drops: List<MonsterDrop>?
)

class MonsterDrop(
    @JsonAlias("itemId") val itemId: String,
    @JsonAlias("chance") val chance: Double
)