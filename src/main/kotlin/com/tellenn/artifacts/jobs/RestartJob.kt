package com.tellenn.artifacts.jobs

import org.apache.logging.log4j.LogManager
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Job to restart the application daily at 00:00.
 */
@Component
class RestartJob(private val context: ConfigurableApplicationContext) {
    private val log = LogManager.getLogger(RestartJob::class.java)

    /**
     * Shuts down the application at 00:00 every day.
     * When running in Docker with a restart policy, this will trigger a restart.
     */
    @Scheduled(cron = "0 0 0 * * *")
    fun restartApplication() {
        log.info("Daily restart scheduled: Shutting down application context at 00:00")
        context.close()
    }
}
