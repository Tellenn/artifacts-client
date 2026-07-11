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
import java.io.InterruptedIOException
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
    @param:Lazy private val crafterJob: CrafterJob,
    @param:Lazy private val fighterJob: FighterJob,
    @param:Lazy private val alchemistJob: AlchemistJob,
    @param:Lazy private val minerJob: MinerJob,
    @param:Lazy private val woodworkerJob: WoodworkerJob,
    private val missionMetrics: MissionMetrics
) {
    private val logger = LoggerFactory.getLogger(ThreadService::class.java)

    companion object {
        /** Delay before respawning a default-behavior thread that exited unexpectedly. */
        private const val RESTART_DELAY_MS = 1000L

        /** Slice between re-interrupts while waiting for a thread to die. */
        private const val JOIN_SLICE_MS = 1000L
    }

    /**
     * Temps maximum d'attente de la mort réelle d'un thread avant d'abandonner un redémarrage.
     * `internal var` (et non paramètre de constructeur, que Spring ne saurait pas résoudre)
     * pour que les tests puissent le raccourcir.
     */
    internal var threadDeathTimeoutMs = 30_000L

    // Map to store character threads by character name
    private val characterThreads = ConcurrentHashMap<String, ManagedCharacterThread>()

    /**
     * Executes the default behavior for a character based on their job.
     * This is the main loop that runs when a character is not on a mission.
     *
     * @param config The character configuration
     */
    private fun runDefaultBehavior(config: CharacterConfig) {
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
        } catch (e: Exception) {
            onDefaultBehaviorStopped(config, e)
        } finally {
            logger.info("Character ${config.name} thread is exiting")
        }
    }

    /**
     * Decides what to do when a character's default behavior loop stops on an exception.
     *
     * The exception type is NOT a reliable signal: a timed-out API call surfaces as a
     * [java.net.SocketTimeoutException], which extends [InterruptedIOException] and therefore looks
     * exactly like a genuine thread interruption. The authoritative signal is [ManagedCharacterThread.isOnMission],
     * which every mission/reservation path sets to `true` *before* interrupting the thread.
     *
     * - Thread no longer the registered one (stopped or already replaced) → just exit.
     * - A mission owns the character (`isOnMission == true`) → exit; the mission framework restarts it.
     * - Anything else (spurious interrupt, transient API timeout, job failure) → restart so the
     *   character does not die permanently.
     */
    private fun onDefaultBehaviorStopped(config: CharacterConfig, cause: Exception) {
        val managed = characterThreads[config.name]
        val isCurrentRegisteredThread = managed?.thread === Thread.currentThread()
        val isMissionTakeover = managed?.isOnMission?.get() == true

        if (!isCurrentRegisteredThread || isMissionTakeover) {
            logger.info("Character ${config.name} default behavior interrupted")
            if (cause is InterruptedException || cause is InterruptedIOException) {
                Thread.currentThread().interrupt() // Preserve interrupt status
            }
            return
        }

        logger.warn(
            "Character ${config.name} default behavior stopped unexpectedly ({}), restarting thread",
            cause.javaClass.simpleName, cause
        )
        restartAfterUnexpectedExit(config)
    }

    /** Restarts a character's default behavior after an unexpected exit, leaving the map entry intact. */
    private fun restartAfterUnexpectedExit(config: CharacterConfig) {
        try {
            Thread.sleep(RESTART_DELAY_MS)
            internalStartThread(config)
        } catch (restartException: Exception) {
            logger.error("Failed to restart character thread for ${config.name}", restartException)
        }
    }

    /**
     * Interrupts a thread and waits for it to actually die, re-interrupting on every slice in case
     * the job consumed the interruption then blocked again (cooldown wait, hung HTTP call).
     *
     * @return true if the thread is dead, false if it survived [threadDeathTimeoutMs]
     */
    private fun stopAndAwaitDeath(managedThread: ManagedCharacterThread): Boolean {
        val thread = managedThread.thread
        if (!thread.isAlive) return true
        val deadline = System.currentTimeMillis() + threadDeathTimeoutMs
        while (thread.isAlive && System.currentTimeMillis() < deadline) {
            thread.interrupt()
            try {
                thread.join(JOIN_SLICE_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return !thread.isAlive
            }
        }
        return !thread.isAlive
    }

    /**
     * Internal method to start a character thread.
     *
     * Invariant anti-duplication : refuse de démarrer si le thread enregistré est encore vivant —
     * deux threads sur le même personnage produisent des 486 « action already in progress » et des
     * désyncs de position/inventaire (cluster de minuit). Le thread courant est exempté : le chemin
     * d'auto-redémarrage après crash ([restartAfterUnexpectedExit]) s'exécute sur le thread mourant.
     *
     * @param config The character configuration
     * @return true if the thread was started, false if a live thread already exists
     */
    private fun internalStartThread(config: CharacterConfig): Boolean {
        val managedThread = characterThreads[config.name]
        val existingThread = managedThread?.thread
        if (existingThread != null && existingThread.isAlive && existingThread !== Thread.currentThread()) {
            logger.error("Refusing to start a duplicate thread for ${config.name}: previous thread is still alive")
            return false
        }
        val isOnMission = managedThread?.isOnMission ?: AtomicBoolean(false)
        val currentPriority = managedThread?.currentPriority ?: AtomicReference(MissionPriority.DEFAULT)

        val thread = Thread {
            runDefaultBehavior(config)
        }
        thread.name = config.name
        thread.start()

        characterThreads[config.name] = ManagedCharacterThread(config, thread, isOnMission, currentPriority)
        missionMetrics.registerCharacter(
            config.name,
            threadAlive = { characterThreads[config.name]?.thread?.isAlive == true },
            onMission = { characterThreads[config.name]?.isOnMission?.get() == true },
        )
        logger.info("Thread started for character: ${config.name}")
        return true
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

        // Convention isOnMission (cf. onDefaultBehaviorStopped) : signale que l'interruption est
        // intentionnelle, sinon le thread mourant se relancerait lui-même en parallèle du nôtre.
        managedThread.isOnMission.set(true)
        if (!stopAndAwaitDeath(managedThread)) {
            logger.error("Thread for $characterName refuses to die after ${threadDeathTimeoutMs}ms, NOT starting a duplicate")
            managedThread.isOnMission.set(false)
            return false
        }

        managedThread.isOnMission.set(false)
        managedThread.currentPriority.set(MissionPriority.DEFAULT)
        return internalStartThread(managedThread.config)
    }
    
    /**
     * Reserves a character for a boss fight without interrupting its thread.
     * Used for the "master" character whose thread IS the boss fight caller.
     *
     * @param characterName The name of the character to reserve
     * @return true if the reservation succeeded, false if the character is already on a mission
     */
    fun reserveCharacter(characterName: String): Boolean {
        val managedThread = getManagedThread(characterName) ?: return false
        return managedThread.isOnMission.compareAndSet(false, true).also { reserved ->
            if (reserved) managedThread.currentPriority.set(MissionPriority.HUMAN_ORDER)
        }
    }

    /**
     * Reserves a character for a boss fight, interrupts its thread, and waits for it to stop.
     * Used for "slave" characters whose threads must be stopped before the boss fight takes over.
     *
     * @param characterName The name of the character to reserve and interrupt
     * @return true if the reservation succeeded, false if the character is already on a mission
     */
    fun reserveAndInterruptCharacter(characterName: String): Boolean {
        val managedThread = getManagedThread(characterName) ?: return false
        if (!managedThread.isOnMission.compareAndSet(false, true)) return false
        managedThread.currentPriority.set(MissionPriority.HUMAN_ORDER)
        if (!stopAndAwaitDeath(managedThread)) {
            logger.error("Thread for $characterName refuses to die after ${threadDeathTimeoutMs}ms, aborting boss fight reservation")
            managedThread.isOnMission.set(false)
            managedThread.currentPriority.set(MissionPriority.DEFAULT)
            return false
        }
        return true
    }

    /**
     * Releases a boss fight reservation for the master character without restarting its thread.
     * The master's thread continues running after this call.
     *
     * @param characterName The name of the character to release
     */
    fun releaseCharacter(characterName: String) {
        val managedThread = getManagedThread(characterName) ?: return
        managedThread.isOnMission.set(false)
        managedThread.currentPriority.set(MissionPriority.DEFAULT)
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

        logger.info("Executing synchronous mission for character: $characterName with priority: $priority")

        // Try to interrupt with priority check
        if (!interruptWithPriority(characterName, priority)) {
            managedThread.isOnMission.set(false)
            return false
        }

        // Abandon AVANT le try/finally : son restartCharacterThread ne doit pas s'exécuter ici,
        // et exécuter la mission en concurrence avec le thread par défaut vivant reproduirait
        // la duplication (486/désyncs) que cet invariant interdit.
        if (!stopAndAwaitDeath(managedThread)) {
            logger.error("Thread for $characterName refuses to die after ${threadDeathTimeoutMs}ms, aborting mission")
            managedThread.isOnMission.set(false)
            managedThread.currentPriority.set(MissionPriority.DEFAULT)
            return false
        }

        return try {
            // Execute the mission in the current thread
            missionMetrics.timeMission(characterName, priority.name) { mission() }

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

        logger.info("Assigning async mission to character: $characterName with priority: $priority")

        // Try to interrupt with priority check
        if (!interruptWithPriority(characterName, priority)) {
            managedThread.isOnMission.set(false)
            return false
        }

        // Même invariant que executeMissionSync : pas de mission tant que l'ancien thread vit
        if (!stopAndAwaitDeath(managedThread)) {
            logger.error("Thread for $characterName refuses to die after ${threadDeathTimeoutMs}ms, aborting async mission")
            managedThread.isOnMission.set(false)
            managedThread.currentPriority.set(MissionPriority.DEFAULT)
            return false
        }

        return try {
            // Execute the mission in a new thread
            val missionThread = Thread {
                try {
                    missionMetrics.timeMission(characterName, priority.name) { mission() }
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
