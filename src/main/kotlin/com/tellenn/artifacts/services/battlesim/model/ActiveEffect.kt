package com.tellenn.artifacts.services.battlesim.model

data class ActiveEffect(val code: String, val value: Int)

class HealPotion(val code: String, val healPerUse: Int, var remaining: Int)

/** Copie fraîche d'une potion pour repartir avec le stock de départ à chaque simulation (Combatant est mutable). */
fun HealPotion.copyFresh(): HealPotion = HealPotion(code, healPerUse, remaining)
