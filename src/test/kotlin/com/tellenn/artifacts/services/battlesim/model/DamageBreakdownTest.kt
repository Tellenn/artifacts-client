package com.tellenn.artifacts.services.battlesim.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DamageBreakdownTest {
    @Test
    fun `total sums all elements`() {
        assertEquals(10, DamageBreakdown(fire = 1, earth = 2, water = 3, air = 4).total)
    }

    @Test
    fun `crit multiplies each element by 1_5 rounding half up`() {
        // 3 -> 4.5 -> 5 (half up), 2 -> 3.0 -> 3
        val crit = DamageBreakdown(fire = 3, earth = 2, water = 0, air = 0).crit()
        assertEquals(5, crit.fire)
        assertEquals(3, crit.earth)
    }
}
