package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.config.CharacterConfig
import org.apache.logging.log4j.LogManager

private val log = LogManager.getLogger("CrafterJob")

/**
 * Function for characters with the "crafter" job.
 *
 * @param config The character configuration
 * @param character The character object
 */
fun runCrafterFunction(config: CharacterConfig, character: ArtifactsCharacter) {
    log.info("Running crafter function for character: ${character.name}")

    while (!Thread.currentThread().isInterrupted) {
        // Simulate crafter-specific work
        log.info("Crafter ${character.name} is crafting items...")

        try {
            // Sleep for a bit to simulate work and allow for interruption
            Thread.sleep(5000) // 5 seconds
        } catch (e: InterruptedException) {
            // Restore the interrupted status
            Thread.currentThread().interrupt()
            log.info("Crafter ${character.name} thread was interrupted during sleep")
            break
        }
    }
}