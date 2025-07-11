package com.tellenn.artifacts

import com.fasterxml.jackson.databind.ObjectMapper
import com.tellenn.artifacts.clients.ServerStatusClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.config.CharacterConfig
import com.tellenn.artifacts.services.CharacterSyncService
import com.tellenn.artifacts.services.ItemSyncService
import com.tellenn.artifacts.services.MapSyncService
import com.tellenn.artifacts.services.MonsterSyncService
import com.tellenn.artifacts.services.WebSocketService
import com.tellenn.artifacts.utils.TimeSync
import lombok.extern.slf4j.Slf4j
import org.apache.logging.log4j.LogManager
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import jakarta.annotation.PreDestroy

@Slf4j
@Component
class MainRuntime(
    private val serverClient: ServerStatusClient,
    private val objectMapper: ObjectMapper,
    private val timeSync: TimeSync,
    private val itemSyncService: ItemSyncService,
    private val mapSyncService: MapSyncService,
    private val monsterSyncService: MonsterSyncService,
    private val characterSyncService: CharacterSyncService,
    private val webSocketService: WebSocketService
) : ApplicationRunner {

    private val log = LogManager.getLogger(MainRuntime::class.java)

    override fun run(args: ApplicationArguments?) {
        // Call the server to get the information
        val serverStatus = serverClient.getServerStatus()
        timeSync.syncWithServerTime(serverStatus.data.serverTime)
        log.info("Time synchronized with server. Offset: ${timeSync.currentOffset}")

        log.info(objectMapper.writeValueAsString(serverStatus.data))
        AppConfig.maxLevel = serverStatus.data.maxLevel

        log.info(objectMapper.writeValueAsString(AppConfig))

        // Synchronize items from the server
        val syncedItemsCount = itemSyncService.syncAllItems()
        log.info("Items synchronized with server. Total items: $syncedItemsCount")

        // Synchronize maps from the server
        val syncedMapChunksCount = mapSyncService.syncWholeMap()
        log.info("Maps synchronized with server. Total map chunks: $syncedMapChunksCount")

        // Synchronize monsters from the server
        val syncedMonstersCount = monsterSyncService.syncAllMonsters()
        log.info("Monsters synchronized with server. Total monsters: $syncedMonstersCount")

        // Synchronize characters from config
        val characterMap = characterSyncService.syncPredefinedCharacters()
        log.info("Characters synchronized with config. Total characters: ${characterMap.size}")

        // Start a thread for each character
        startCharacterThreads(characterMap)

        // Connect to the WebSocket server
        log.info("Connecting to WebSocket server...")
        val connected = webSocketService.connect()
        if (connected) {
            log.info("Successfully connected to WebSocket server")
        } else {
            log.error("Failed to connect to WebSocket server")
        }
    }

    /**
     * Starts a thread for each character in the map.
     * Each thread runs a placeholder function.
     * Stores thread references in the WebSocketService.
     * Includes exception handling to restart character threads if they fail to start.
     *
     * @param characterMap Map of CharacterConfig to ArtifactsCharacter
     */
    private fun startCharacterThreads(characterMap: Map<CharacterConfig, ArtifactsCharacter>) {
        log.info("Starting threads for ${characterMap.size} characters")

        for ((config, character) in characterMap) {
            try {
                // Check if a thread already exists for this character
                if (webSocketService.getCharacterThread(character.name) != null) {
                    log.info("Thread for character ${character.name} already exists, skipping")
                    continue
                }

                val thread = Thread {
                    runCharacterPlaceholderFunction(config, character)
                }
                thread.name = "Character-Thread-${character.name}"

                // Store the thread reference in the WebSocketService
                webSocketService.addCharacterThread(character.name, thread)

                thread.start()
                log.info("Started thread for character: ${character.name}")
            } catch (e: Exception) {
                log.error("Exception occurred while starting thread for character: ${character.name}", e)

                // Wait a moment before attempting to restart
                try {
                    log.info("Attempting to restart thread for character: ${character.name}")
                    Thread.sleep(1000) // Wait 1 second before restarting

                    // Create a new thread and start it
                    val newThread = Thread {
                        runCharacterPlaceholderFunction(config, character)
                    }
                    newThread.name = "Character-Thread-${character.name}-Restarted"

                    // Store the thread reference in the WebSocketService
                    webSocketService.addCharacterThread(character.name, newThread)

                    newThread.start()
                    log.info("Successfully restarted thread for character: ${character.name}")
                } catch (e2: Exception) {
                    log.error("Failed to restart thread for character: ${character.name}", e2)
                }
            }
        }
    }

    /**
     * Restarts a specific character thread.
     *
     * @param characterName The name of the character whose thread should be restarted
     * @return true if the thread was restarted, false if the character doesn't exist
     */
    fun restartCharacterThread(characterName: String): Boolean {
        // Get the character and config for the given name
        val characterEntry = characterSyncService.getCharacterMap().entries.find { 
            it.value.name == characterName 
        } ?: return false

        val config = characterEntry.key
        val character = characterEntry.value

        // Interrupt the existing thread if it exists
        val existingThread = webSocketService.getCharacterThread(characterName)
        if (existingThread != null && existingThread.isAlive) {
            webSocketService.interruptCharacterThread(characterName)
            log.info("Interrupted existing thread for character: $characterName")
        }

        // Create and start a new thread
        val newThread = Thread {
            runCharacterPlaceholderFunction(config, character)
        }
        newThread.name = "Character-Thread-$characterName"

        // Update the thread reference in the WebSocketService
        webSocketService.addCharacterThread(characterName, newThread)

        newThread.start()
        log.info("Restarted thread for character: $characterName")

        return true
    }

    /**
     * Restarts all character threads.
     *
     * @return The number of threads that were restarted
     */
    fun restartAllCharacterThreads(): Int {
        log.info("Restarting all character threads")
        var count = 0

        val characterMap = characterSyncService.getCharacterMap()
        for ((config, character) in characterMap) {
            // Interrupt the existing thread if it exists
            val existingThread = webSocketService.getCharacterThread(character.name)
            if (existingThread != null && existingThread.isAlive) {
                webSocketService.interruptCharacterThread(character.name)
                log.info("Interrupted existing thread for character: ${character.name}")
            }

            // Create and start a new thread
            val newThread = Thread {
                runCharacterPlaceholderFunction(config, character)
            }
            newThread.name = "Character-Thread-${character.name}"

            // Update the thread reference in the WebSocketService
            webSocketService.addCharacterThread(character.name, newThread)

            newThread.start()
            log.info("Restarted thread for character: ${character.name}")
            count++
        }

        return count
    }

    /**
     * Placeholder function that will be executed by each character thread.
     * Currently just logs information about the character.
     * Handles interruption by checking Thread.currentThread().isInterrupted.
     * If an exception occurs, the character thread will be restarted.
     *
     * @param config The character configuration
     * @param character The character object
     */
    private fun runCharacterPlaceholderFunction(config: CharacterConfig, character: ArtifactsCharacter) {
        log.info("Running placeholder function for character: ${character.name}")
        log.info("Character details - Name: ${character.name}, Level: ${character.level}, Job: ${config.job}")

        try {
            // This is a placeholder function that will be expanded in the future
            // For now, we'll just have it run in a loop until interrupted
            while (!Thread.currentThread().isInterrupted) {
                // Simulate some work
                log.info("Character ${character.name} is performing work...")

                try {
                    // Sleep for a bit to simulate work and allow for interruption
                    Thread.sleep(5000) // 5 seconds
                } catch (e: InterruptedException) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt()
                    log.info("Character ${character.name} thread was interrupted during sleep")
                    break
                }
            }
        } catch (e: Exception) {
            log.error("Error in character thread for ${character.name}", e)

            // Restart the character thread after a brief delay
            try {
                log.info("Restarting character thread for ${character.name} after exception")
                Thread.sleep(1000) // Wait 1 second before restarting
                restartCharacterThread(character.name)
            } catch (e: Exception) {
                log.error("Failed to restart character thread for ${character.name}", e)
            }
        } finally {
            log.info("Character ${character.name} thread is exiting")
            // Remove the thread from the WebSocketService when it exits
            webSocketService.removeCharacterThread(character.name)
        }
    }

    /**
     * Cleans up resources when the application is shutting down.
     * This method is called automatically by Spring when the application context is being destroyed.
     */
    @PreDestroy
    fun cleanup() {
        log.info("Application shutting down, cleaning up resources...")

        // Interrupt all character threads
        webSocketService.interruptAllCharacterThreads()

        // Shutdown the WebSocket service
        webSocketService.shutdown()

        log.info("Cleanup completed")
    }
}
