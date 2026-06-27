package com.tellenn.artifacts.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class DurationFormatTest {

    @Test
    fun `renders a whole number of hours`() {
        assertEquals("5h", Duration.ofHours(5).toHumanReadable())
    }

    @Test
    fun `keeps the two most significant units`() {
        // 3h 28m 42s -> drops the seconds
        assertEquals("3h 28m", Duration.ofMillis(12522468).toHumanReadable())
    }

    @Test
    fun `renders whole days`() {
        assertEquals("7d", Duration.ofDays(7).toHumanReadable())
    }

    @Test
    fun `renders seconds only when below a minute`() {
        assertEquals("45s", Duration.ofSeconds(45).toHumanReadable())
    }

    @Test
    fun `renders zero as 0s`() {
        assertEquals("0s", Duration.ZERO.toHumanReadable())
    }
}
