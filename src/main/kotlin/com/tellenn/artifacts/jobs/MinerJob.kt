package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.config.CharacterConfig
import org.apache.logging.log4j.LogManager

private val log = LogManager.getLogger("MinerJob")

/**
 * Function for characters with the "miner" job.
 *
 * @param config The character configuration
 * @param character The character object
 */
fun runMinerFunction(config: CharacterConfig, character: ArtifactsCharacter) {
    log.info("Running miner function for character: ${character.name}")

    while (!Thread.currentThread().isInterrupted) {
        // Simulate miner-specific work
        log.info("Miner ${character.name} is mining resources...")

        try {
            // Sleep for a bit to simulate work and allow for interruption
            Thread.sleep(5000) // 5 seconds
        } catch (e: InterruptedException) {
            // Restore the interrupted status
            Thread.currentThread().interrupt()
            log.info("Miner ${character.name} thread was interrupted during sleep")
            break
        }
    }
}