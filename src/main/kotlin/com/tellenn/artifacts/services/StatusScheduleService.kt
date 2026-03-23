package com.tellenn.artifacts.services

import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class StatusScheduleService {
    private val log = LoggerFactory.getLogger(StatusScheduleService::class.java)

    @Value("\${artifacts.api.url}")
    lateinit var url: String

    @Value("\${artifacts.api.key}")
    lateinit var key: String

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Scheduled task to check the status of the API every 10 minutes.
     * Uses fixedRate to ensure it runs every 600,000 ms from the start of the previous execution.
     */
    @Scheduled(fixedRate = 600000)
    fun requestApiStatus() {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $key")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val hoursRemaining = response.headers["x-ratelimit-remaining-hour"].orEmpty()
                val minutesRemaining = response.headers["x-ratelimit-remaining-minute"].orEmpty()
                val secondsRemaining = response.headers["x-ratelimit-remaining-second"].orEmpty()
                log.info("API status check completed. Calls remaining: $hoursRemaining /h, $minutesRemaining /m, $secondsRemaining /s")
            }
        } catch (e: Exception) {
            log.error("Error during scheduled API status check: ${e.message}", e)
        }
    }
}
