package com.tellenn.artifacts.services.battlesim.model

data class FightOutcome(
    val charactersWin: Boolean,
    val turns: Int,
    val finalHp: Map<String, Int>,
    val logs: List<String>,
)
