package com.tellenn.artifacts.clients.responses

import org.slf4j.helpers.Util

class SimulationResult(
    val wins: Int,
    val losses: Int,
    val winrate: Int,
    val results: List<CombatResult>
)

class CombatResult(
    val result: String,
    val turns: Int,
    val logs: List<String>,
    val character_results: List<CharResult>
)

class CharResult(
    val final_hp: Int,
    val utility1_slot_quantity: Int,
    val utility2_slot_quantity: Int
)