package com.tellenn.artifacts.utils

import java.time.Instant
import java.time.Duration
import org.springframework.stereotype.Component

@Component
@Suppress("unused")
class TimeSync {

     private var offset: Duration = Duration.ZERO

    val currentOffset: Duration
        get() = offset



    fun syncWithServerTime(serverTime: Instant) {
        val localNow = Instant.now()
        offset = Duration.between(localNow, serverTime)
    }
    
    fun now(): Instant {
        return Instant.now().plus(offset)
    }

}