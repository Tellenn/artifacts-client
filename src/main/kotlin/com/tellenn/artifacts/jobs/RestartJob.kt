package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.services.ThreadService
import com.tellenn.artifacts.services.sync.CharacterSyncService
import org.apache.logging.log4j.LogManager
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RestartJob(private val threadService: ThreadService, private val characterSyncService: CharacterSyncService) {
    private val log = LogManager.getLogger(RestartJob::class.java)

    /**
     * Restart every character thread, removing "blackages" if needed
     */
    @Scheduled(cron = "0 0 0 * * *")
    fun restartApplication() {

        log.info("Daily restart scheduled: Shutting down application context at 00:00")
        threadService.stopAllThreads()

        characterSyncService.syncPredefinedCharacters().map { (conf,_) ->
            threadService.startCharacterThread(conf)
        }
    }
}
