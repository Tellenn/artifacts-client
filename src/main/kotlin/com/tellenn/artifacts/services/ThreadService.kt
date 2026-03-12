package com.tellenn.artifacts.services

import com.tellenn.artifacts.config.CharacterConfig
import com.tellenn.artifacts.exceptions.UnknownJobException
import com.tellenn.artifacts.jobs.AlchemistJob
import com.tellenn.artifacts.jobs.CrafterJob
import com.tellenn.artifacts.jobs.FighterJob
import com.tellenn.artifacts.jobs.MinerJob
import com.tellenn.artifacts.jobs.WoodworkerJob
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Mission priority levels.
 * Higher priority missions can interrupt lower priority missions.
 */
enum class MissionPriority(val level: Int) {
    /** Default behavior - lowest priority, can be interrupted by anything */
    DEFAULT(0),
    
    /** Automatic missions from WebSocket events - low priority */
    AUTOMATIC(1),
    
    /** Human-ordered tasks - highest priority, cannot be interrupted */
    HUMAN_ORDER(2);
    
    fun canInterrupt(other: MissionPriority): Boolean {
        return this.level > other.level
    }
}

/**
 * Represents a managed character thread with default behavior and mission capabilities.
 */
private class ManagedCharacterThread(
    val config: CharacterConfig,
    val thread: Thread,
    val isOnMission: AtomicBoolean = AtomicBoolean(false),
    val currentPriority: AtomicReference<MissionPriority> = AtomicReference(MissionPriority.DEFAULT)
)

/**
 * Service responsible for managing character threads and assigning them missions.
 */
@Service
class ThreadService(
    @Lazy private val crafterJob: CrafterJob,
    @Lazy private val fighterJob: FighterJob,
    @Lazy private val alchemistJob: AlchemistJob,
    @Lazy private val minerJob: MinerJob,
    @Lazy private val woodworkerJob: WoodworkerJob
) {
    private val logger = LoggerFactory.getLogger(ThreadService::class.java)

    // Map to store character threads by character name
    private val characterThreads = ConcurrentHashMap<String, ManagedCharacterThread>()

    /**
     * Executes the default behavior for a character based on their job.
     * This is the main loop that runs when a character is not on a mission.
     *
     * @param config The character configuration
     */
    private fun runDefaultBehavior(config: CharacterConfig) {
        logger.info("Character details - Name: ${config.name}, Job: ${config.job}")

        try {
            // Create and run the appropriate job based on the character's job type
            when (config.job.lowercase()) {
                "crafter" -> crafterJob.run(config.name)
                "fighter" -> fighterJob.run(config.name)
                "alchemist" -> alchemistJob.run(config.name)
                "miner" -> minerJob.run(config.name)
                "woodworker" -> woodworkerJob.run(config.name)
                else -> {
                    logger.error("Unknown job '${config.job}' for character ${config.name}")
                    throw UnknownJobException(config.job, config.name)
                }
            }
        } catch (_: InterruptedException) {
            // Thread was interrupted, likely for a mission
            logger.info("Character ${config.name} default behavior interrupted")
            Thread.currentThread().interrupt() // Preserve interrupt status
        } catch (e: Exception) {
            logger.error("Error in character thread for ${config.name}", e)

            // Restart the character thread after a brief delay
            try {
                logger.info("Restarting character thread for ${config.name} after exception")
                Thread.sleep(1000) // Wait 1 second before restarting
                internalStartThread(config)
            } catch (restartException: Exception) {
                logger.error("Failed to restart character thread for ${config.name}", restartException)
            }
        } finally {
            logger.info("Character ${config.name} thread is exiting")
        }
    }

    /**
     * Internal method to start a character thread.
     *
     * @param config The character configuration
     */
    private fun internalStartThread(config: CharacterConfig) {
        val managedThread = characterThreads[config.name]
        val isOnMission = managedThread?.isOnMission ?: AtomicBoolean(false)
        val currentPriority = managedThread?.currentPriority ?: AtomicReference(MissionPriority.DEFAULT)

        val thread = Thread {
            runDefaultBehavior(config)
        }
        thread.name = config.name
        thread.start()

        characterThreads[config.name] = ManagedCharacterThread(config, thread, isOnMission, currentPriority)
        logger.info("Thread started for character: ${config.name}")
    }

    /**
     * Creates and starts a new character thread.
     *
     * @param config The character configuration
     * @return true if the thread was started successfully, false otherwise
     */
    fun startCharacterThread(config: CharacterConfig): Boolean {
        return try {
            // Check if a thread already exists for this character
            if (characterThreads.containsKey(config.name)) {
                logger.warn("Thread for character ${config.name} already exists")
                return true
            }

            internalStartThread(config)
            logger.info("Started thread for character: ${config.name}")
            true
        } catch (e: Exception) {
            logger.error("Failed to start thread for character: ${config.name}", e)
            false
        }
    }

    /**
     * Gets a character's managed thread.
     *
     * @param characterName The name of the character
     * @return The managed thread, or null if none exists
     */
    private fun getManagedThread(characterName: String): ManagedCharacterThread? {
        return characterThreads[characterName]
    }

    /**
     * Checks if a character thread exists.
     *
     * @param characterName The name of the character
     * @return true if a thread exists for this character, false otherwise
     */
    fun hasCharacterThread(characterName: String): Boolean {
        return characterThreads.containsKey(characterName)
    }

    /**
     * Interrupts a specific character thread.
     *
     * @param characterName The name of the character whose thread should be interrupted
     * @return true if the thread was interrupted, false if the thread doesn't exist
     */
    fun interruptCharacterThread(characterName: String): Boolean {
        val managedThread = getManagedThread(characterName) ?: return false

        logger.info("Interrupting thread for character: $characterName")
        managedThread.isOnMission.set(true)
        managedThread.thread.interrupt()
        return true
    }

    /**
     * Restarts a specific character thread to resume default behavior.
     *
     * @param characterName The name of the character whose thread should be restarted
     * @return true if the thread was restarted, false if the thread doesn't exist
     */
    fun restartCharacterThread(characterName: String): Boolean {
        val managedThread = getManagedThread(characterName) ?: return false
        
        logger.info("Restarting thread for character: $characterName")
        
        // Wait for the current thread to finish if it's still running
        if (managedThread.thread.isAlive) {
            try {
                managedThread.thread.join(5000) // Wait up to 5 seconds
            } catch (_: InterruptedException) {
                logger.warn("Interrupted while waiting for thread to finish for $characterName")
                Thread.currentThread().interrupt()
            }
        }
        
        managedThread.isOnMission.set(false)
        managedThread.currentPriority.set(MissionPriority.DEFAULT)
        internalStartThread(managedThread.config)
        return true
    }
    
    /**
     * Interrupts a character thread with priority checking.
     *
     * @param characterName The name of the character whose thread should be interrupted
     * @param newPriority The priority of the new mission
     * @return true if interrupted successfully, false if priority is too low or thread doesn't exist
     */
    private fun interruptWithPriority(characterName: String, newPriority: MissionPriority): Boolean {
        val managedThread = getManagedThread(characterName) ?: return false
        
        val currentPriority = managedThread.currentPriority.get()
        
        // Check if new priority can interrupt current priority
        if (!newPriority.canInterrupt(currentPriority)) {
            logger.warn("Cannot interrupt character $characterName: new priority $newPriority (level ${newPriority.level}) cannot interrupt current priority $currentPriority (level ${currentPriority.level})")
            return false
        }
        
        logger.info("Interrupting thread for character: $characterName (current priority: $currentPriority, new priority: $newPriority)")
        managedThread.currentPriority.set(newPriority)
        managedThread.thread.interrupt()
        return true
    }

    /**
     * Executes a mission synchronously in the calling thread.
     * Interrupts the character's default thread, executes the mission, then restarts default behavior.
     * The mission executes in the caller's thread (e.g., WebSocket event handler thread).
     *
     * @param characterName The name of the character to assign the mission to
     * @param priority The priority of this mission (DEFAULT, AUTOMATIC, HUMAN_ORDER)
     * @param mission The mission function to execute
     * @return true if the mission was successfully executed, false otherwise
     */
    fun executeMissionSync(characterName: String, priority: MissionPriority = MissionPriority.AUTOMATIC, mission: () -> Unit): Boolean {
        val managedThread = getManagedThread(characterName)
        
        if (managedThread == null) {
            logger.warn("Cannot execute mission: thread for character $characterName does not exist")
            return false
        }

        // Check if already on a mission
        if (!managedThread.isOnMission.compareAndSet(false, true)) {
            val currentPriority = managedThread.currentPriority.get()
            logger.warn("Character $characterName is already on a mission with priority $currentPriority, cannot execute new mission with priority $priority")
            return false
        }

        return try {
            logger.info("Executing synchronous mission for character: $characterName with priority: $priority")
            
            // Try to interrupt with priority check
            if (!interruptWithPriority(characterName, priority)) {
                managedThread.isOnMission.set(false)
                return false
            }
            
            // Wait for the thread to finish
            if (managedThread.thread.isAlive) {
                managedThread.thread.join(5000) // Wait up to 5 seconds
            }
            
            // Execute the mission in the current thread
            mission()
            
            logger.info("Mission completed for character: $characterName")
            true
        } catch (e: Exception) {
            logger.error("Error executing mission for character $characterName", e)
            false
        } finally {
            // Always restart default behavior after mission
            restartCharacterThread(characterName)
        }
    }

    /**
     * Assigns a mission to a character by interrupting their current thread,
     * executing the mission in a separate thread, and then restarting the default behavior.
     * Use this for long-running missions that shouldn't block the caller.
     *
     * @param characterName The name of the character to assign the mission to
     * @param priority The priority of this mission (DEFAULT, AUTOMATIC, HUMAN_ORDER)
     * @param mission The mission function to execute
     * @return true if the mission was successfully assigned, false otherwise
     */
    fun assignMissionAsync(characterName: String, priority: MissionPriority = MissionPriority.AUTOMATIC, mission: () -> Unit): Boolean {
        val managedThread = getManagedThread(characterName)
        
        if (managedThread == null) {
            logger.warn("Cannot assign mission: thread for character $characterName does not exist")
            return false
        }

        // Check if already on a mission
        if (!managedThread.isOnMission.compareAndSet(false, true)) {
            val currentPriority = managedThread.currentPriority.get()
            logger.warn("Character $characterName is already on a mission with priority $currentPriority, cannot assign new mission with priority $priority")
            return false
        }

        return try {
            logger.info("Assigning async mission to character: $characterName with priority: $priority")
            
            // Try to interrupt with priority check
            if (!interruptWithPriority(characterName, priority)) {
                managedThread.isOnMission.set(false)
                return false
            }
            
            // Wait for the thread to finish
            if (managedThread.thread.isAlive) {
                managedThread.thread.join(5000) // Wait up to 5 seconds
            }
            
            // Execute the mission in a new thread
            val missionThread = Thread {
                try {
                    mission()
                    logger.info("Async mission completed for character: $characterName")
                } catch (e: Exception) {
                    logger.error("Error executing async mission for character $characterName", e)
                } finally {
                    // Always restart default behavior after mission
                    restartCharacterThread(characterName)
                }
            }
            missionThread.name = "${characterName}-mission"
            missionThread.start()
            
            true
        } catch (e: Exception) {
            logger.error("Error assigning async mission for character $characterName", e)
            
            // Reset mission flag and attempt to restart the thread
            managedThread.isOnMission.set(false)
            try {
                restartCharacterThread(characterName)
            } catch (restartException: Exception) {
                logger.error("Failed to restart thread after mission assignment failure for character $characterName", restartException)
            }
            
            false
        }
    }

    /**
     * Stops a specific character thread and removes it from the map.
     *
     * @param characterName The name of the character whose thread should be stopped
     * @return true if the thread was stopped, false if the thread doesn't exist
     */
    fun stopCharacterThread(characterName: String): Boolean {
        val managedThread = characterThreads.remove(characterName) ?: return false

        logger.info("Stopping thread for character: $characterName")
        managedThread.thread.interrupt()
        return true
    }

    /**
     * Stops all character threads.
     */
    fun stopAllThreads() {
        logger.info("Stopping all character threads (${characterThreads.size} threads)")
        
        characterThreads.values.forEach { managedThread ->
            try {
                managedThread.thread.interrupt()
            } catch (e: Exception) {
                logger.error("Error stopping thread: ${managedThread.thread.name}", e)
            }
        }
        
        characterThreads.clear()
        logger.info("All character threads stopped")
    }

    /**
     * Gets the number of active character threads.
     *
     * @return The number of character threads
     */
    fun getActiveThreadCount(): Int {
        return characterThreads.size
    }

    /**
     * Gets a list of all character names with active threads.
     *
     * @return List of character names
     */
    fun getActiveCharacterNames(): List<String> {
        return characterThreads.keys.toList()
    }

    /**
     * Checks if a character is currently on a mission.
     *
     * @param characterName The name of the character
     * @return true if the character is on a mission, false if running in automatic mode or doesn't exist
     */
    fun isCharacterOnMission(characterName: String): Boolean {
        val managedThread = getManagedThread(characterName) ?: return false
        return managedThread.isOnMission.get()
    }
}
