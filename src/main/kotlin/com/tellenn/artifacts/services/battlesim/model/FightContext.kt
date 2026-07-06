package com.tellenn.artifacts.services.battlesim.model

import com.tellenn.artifacts.services.battlesim.engine.Combatant
import kotlin.random.Random

class FightContext(
    val log: MutableList<String>,
    val rng: Random,
) {
    var turn: Int = 0
    lateinit var characters: List<Combatant>
    lateinit var monsters: List<Combatant>

    fun sideOf(c: Combatant): List<Combatant> = if (c.isMonster) monsters else characters
    fun allies(of: Combatant): List<Combatant> = sideOf(of).filter { it !== of && it.isAlive }
    fun enemies(of: Combatant): List<Combatant> =
        (if (of.isMonster) characters else monsters).filter { it.isAlive }
}
