package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.config.CharacterConfig
import org.apache.logging.log4j.LogManager

private val log = LogManager.getLogger("WoodworkerJob")

/**
 * Function for characters with the "woodworker" job.
 *
 * @param config The character configuration
 * @param character The character object
 */
fun runWoodworkerFunction(config: CharacterConfig, character: ArtifactsCharacter) {
    log.info("Running woodworker function for character: ${character.name}")

    while (!Thread.currentThread().isInterrupted) {
        // Simulate woodworker-specific work
        log.info("Woodworker ${character.name} is cutting trees...")

        try {
            // Sleep for a bit to simulate work and allow for interruption
            Thread.sleep(5000) // 5 seconds
        } catch (e: InterruptedException) {
            // Restore the interrupted status
            Thread.currentThread().interrupt()
            log.info("Woodworker ${character.name} thread was interrupted during sleep")
            break
        }
    }
}