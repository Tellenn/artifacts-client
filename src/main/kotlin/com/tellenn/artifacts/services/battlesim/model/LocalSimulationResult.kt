package com.tellenn.artifacts.services.battlesim.model

data class LocalSimulationResult(
    val wins: Int,
    val losses: Int,
    val winrate: Int,
    val avgTurns: Double,
    val results: List<FightOutcome>,
)
