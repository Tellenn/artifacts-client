package com.tellenn.artifacts.services.battlesim.model

import kotlin.math.roundToInt

data class DamageBreakdown(
    val fire: Int = 0,
    val earth: Int = 0,
    val water: Int = 0,
    val air: Int = 0,
) {
    val total: Int get() = fire + earth + water + air

    fun crit(): DamageBreakdown = DamageBreakdown(
        fire = (fire * 1.5).roundToInt(),
        earth = (earth * 1.5).roundToInt(),
        water = (water * 1.5).roundToInt(),
        air = (air * 1.5).roundToInt(),
    )
}
