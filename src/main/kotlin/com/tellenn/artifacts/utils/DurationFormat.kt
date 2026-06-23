package com.tellenn.artifacts.utils

import java.time.Duration

/**
 * Renders a duration as a compact, human-readable string using the two most
 * significant non-zero units (e.g. "5h", "3h 28m", "7d", "45s"). Returns "0s"
 * for a zero/negative duration.
 */
fun Duration.toHumanReadable(): String {
    val parts = buildList {
        if (toDays() > 0) add("${toDays()}d")
        if (toHoursPart() > 0) add("${toHoursPart()}h")
        if (toMinutesPart() > 0) add("${toMinutesPart()}m")
        if (toSecondsPart() > 0) add("${toSecondsPart()}s")
    }
    return if (parts.isEmpty()) "0s" else parts.take(2).joinToString(" ")
}
