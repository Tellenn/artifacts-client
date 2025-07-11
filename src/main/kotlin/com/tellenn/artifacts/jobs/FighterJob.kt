package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.config.CharacterConfig
import org.apache.logging.log4j.LogManager

private val log = LogManager.getLogger("FighterJob")

/**
 * Function for characters with the "fighter" job.
 *
 * @param config The character configuration
 * @param character The character object
 */
fun runFighterFunction(config: CharacterConfig, character: ArtifactsCharacter) {
    log.info("Running fighter function for character: ${character.name}")

    while (!Thread.currentThread().isInterrupted) {
        // Simulate fighter-specific work
        log.info("Fighter ${character.name} is battling monsters...")

        try {
            // Sleep for a bit to simulate work and allow for interruption
            Thread.sleep(5000) // 5 seconds
        } catch (e: InterruptedException) {
            // Restore the interrupted status
            Thread.currentThread().interrupt()
            log.info("Fighter ${character.name} thread was interrupted during sleep")
            break
        }
    }
}