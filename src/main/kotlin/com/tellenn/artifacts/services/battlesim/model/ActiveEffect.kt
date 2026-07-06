package com.tellenn.artifacts.services.battlesim.model

data class ActiveEffect(val code: String, val value: Int)

class HealPotion(val code: String, val healPerUse: Int, var remaining: Int)
