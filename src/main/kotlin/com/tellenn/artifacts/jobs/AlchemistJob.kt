package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.config.CharacterConfig
import org.apache.logging.log4j.LogManager

private val log = LogManager.getLogger("AlchemistJob")

/**
 * Function for characters with the "alchemist" job.
 *
 * @param config The character configuration
 * @param character The character object
 */
fun runAlchemistFunction(config: CharacterConfig, character: ArtifactsCharacter) {
    log.info("Running alchemist function for character: ${character.name}")

    while (!Thread.currentThread().isInterrupted) {
        // Simulate alchemist-specific work
        log.info("Alchemist ${character.name} is brewing potions...")

        try {
            // Sleep for a bit to simulate work and allow for interruption
            Thread.sleep(5000) // 5 seconds
        } catch (e: InterruptedException) {
            // Restore the interrupted status
            Thread.currentThread().interrupt()
            log.info("Alchemist ${character.name} thread was interrupted during sleep")
            break
        }
    }
}