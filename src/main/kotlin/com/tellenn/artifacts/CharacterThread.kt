package com.tellenn.artifacts

import com.tellenn.artifacts.config.CharacterConfig
import com.tellenn.artifacts.exceptions.UnknownJobException
import com.tellenn.artifacts.jobs.AlchemistJob
import com.tellenn.artifacts.jobs.CrafterJob
import com.tellenn.artifacts.jobs.FighterJob
import com.tellenn.artifacts.jobs.MinerJob
import com.tellenn.artifacts.jobs.WoodworkerJob
import org.apache.logging.log4j.LogManager

class CharacterThread(
    private val config : CharacterConfig,
    private val crafterJob: CrafterJob,
    private val fighterJob: FighterJob,
    private val alchemistJob: AlchemistJob,
    private val minerJob: MinerJob,
    private val woodworkerJob: WoodworkerJob,
){

    lateinit var thread : Thread

    private val log = LogManager.getLogger(CharacterThread::class.java)

    fun startThread(){
        thread = Thread {
            runCharacter()
        }
        thread.name = config.name
        thread.start()
    }

    /**
     * Placeholder function that will be executed by each character thread.
     * Calls the appropriate function based on the character's job.
     * Handles interruption by checking Thread.currentThread().isInterrupted.
     * If an exception occurs, the character thread will be restarted.
     *
     * @param config The character configuration
     * @param character The character object
     */
    private fun runCharacter() {
        log.info("Character details - Name: ${config.name}, Job: ${config.job}")

        try {
            // Create and run the appropriate job based on the character's job type
            when (config.job.lowercase()) {
                "crafter" -> crafterJob.run(config.name)
                "fighter" -> fighterJob.run(config.name)
                "alchemist" -> alchemistJob.run(config.name)
                "miner" -> minerJob.run(config.name)
                "woodworker" -> woodworkerJob.run(config.name)
                else -> {
                    log.error("Unknown job '${config.job}' for character ${config.name}")
                    throw UnknownJobException(config.job, config.name)
                }
            }
        } catch (e: Exception) {
            log.error("Error in character thread for ${config.name}", e)

            // Restart the character thread after a brief delay
            try {
                log.info("Restarting character thread for ${config.name} after exception")
                Thread.sleep(1000) // Wait 1 second before restarting
                startThread()
            } catch (e: Exception) {
                log.error("Failed to restart character thread for ${config.name}", e)
            }
        } finally {
            log.error("Character ${config.name} thread is exiting")
        }
    }
}