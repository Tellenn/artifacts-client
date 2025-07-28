package com.tellenn.artifacts

import com.fasterxml.jackson.databind.ObjectMapper
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.clients.ServerStatusClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.config.CharacterConfig
import com.tellenn.artifacts.exceptions.UnknownJobException
import com.tellenn.artifacts.jobs.AlchemistJob
import com.tellenn.artifacts.jobs.CrafterJob
import com.tellenn.artifacts.jobs.FighterJob
import com.tellenn.artifacts.jobs.MinerJob
import com.tellenn.artifacts.jobs.WoodworkerJob
import com.tellenn.artifacts.services.sync.BankItemSyncService
import com.tellenn.artifacts.services.sync.CharacterSyncService
import com.tellenn.artifacts.services.ItemSyncService
import com.tellenn.artifacts.services.MapSyncService
import com.tellenn.artifacts.services.sync.MonsterSyncService
import com.tellenn.artifacts.services.sync.ResourceSyncService
import com.tellenn.artifacts.services.ServerVersionService
import com.tellenn.artifacts.services.WebSocketService
import com.tellenn.artifacts.utils.TimeSync
import lombok.extern.slf4j.Slf4j
import org.apache.logging.log4j.LogManager
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import jakarta.annotation.PreDestroy
import java.lang.Thread.sleep
import java.util.Collections

@Slf4j
@Component
class MainRuntime(
    private val alchemistJob: AlchemistJob,
    private val crafterJob: CrafterJob,
    private val fighterJob: FighterJob,
    private val minerJob: MinerJob,
    private val woodworkerJob: WoodworkerJob,
    private val serverStatusClient: ServerStatusClient,
    private val objectMapper: ObjectMapper,
    private val timeSync: TimeSync,
    private val itemSyncService: ItemSyncService,
    private val mapSyncService: MapSyncService,
    private val monsterSyncService: MonsterSyncService,
    private val characterSyncService: CharacterSyncService,
    private val webSocketService: WebSocketService,
    private val bankItemSyncService: BankItemSyncService,
    private val characterClient: CharacterClient,
    private val accountClient: AccountClient,
    private val resourceSyncService: ResourceSyncService,
    private val serverVersionService: ServerVersionService
) : ApplicationRunner {

    private val log = LogManager.getLogger(MainRuntime::class.java)

    // Thread-safe list that can be safely accessed and modified by multiple threads
    val sharedThreadList: MutableList<Any> = Collections.synchronizedList(ArrayList())

    override fun run(args: ApplicationArguments?) {
        // Call the server to get the information
        val serverStatus = serverStatusClient.getServerStatus()
        timeSync.syncWithServerTime(serverStatus.data.serverTime)
        log.info("Time synchronized with server. Offset: ${timeSync.currentOffset}")

        log.info(objectMapper.writeValueAsString(serverStatus.data))
        AppConfig.maxLevel = serverStatus.data.maxLevel

        log.info(objectMapper.writeValueAsString(AppConfig))
        
        // Get command line arguments
        val forceSync = args?.containsOption("force-sync") ?: false
        log.info("Force sync: $forceSync")
        
        // Check if sync is needed based on server version
        val syncNeeded = serverVersionService.isSyncNeeded(forceSync)
        
        if (syncNeeded) {
            log.info("Server version changed or force sync requested, performing all syncs")
            
            // Synchronize items from the server
            val syncedItemsCount = itemSyncService.syncAllItems()
            log.info("Items synchronized with server. Total items: $syncedItemsCount")
            sleep(1000)
            
            // Synchronize maps from the server
            val syncedMapChunksCount = mapSyncService.syncWholeMap()
            log.info("Maps synchronized with server. Total map chunks: $syncedMapChunksCount")
            sleep(1000)
            
            // Synchronize monsters from the server
            val syncedMonstersCount = monsterSyncService.syncAllMonsters()
            log.info("Monsters synchronized with server. Total monsters: $syncedMonstersCount")
            sleep(1000)
            
            // Synchronize resources from the server
            val syncedResourceCount = resourceSyncService.syncAllResources()
            log.info("Resources synchronized with server. Total resources: $syncedResourceCount")
            sleep(1000)
            
            // Update the server version after all syncs are completed
            serverVersionService.updateServerVersion()
            log.info("Server version updated after all syncs completed")
        } else {
            log.info("Server version unchanged, skipping syncs (except bank)")
        }


        
        // Bank sync is always performed regardless of server version
        val syncedBankItemsCount = bankItemSyncService.syncAllItems()
        log.info("Bank items synchronized. Total items: $syncedBankItemsCount")
        sleep(1000)

        // Connect to the WebSocket server
        log.info("Connecting to WebSocket server...")
        val connected = webSocketService.connect()
        if (connected) {
            log.info("Successfully connected to WebSocket server")
        } else {
            log.error("Failed to connect to WebSocket server")
        }

        // Start threads for existing characters
        startCharacterThreads(characterSyncService.syncPredefinedCharacters())

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
                    runCharacter(config)
                }
                thread.name = character.name

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
                        runCharacter(config)
                    }
                    newThread.name = character.name

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
            runCharacter(config)
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
                runCharacter(config)
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
     * Calls the appropriate function based on the character's job.
     * Handles interruption by checking Thread.currentThread().isInterrupted.
     * If an exception occurs, the character thread will be restarted.
     *
     * @param config The character configuration
     * @param character The character object
     */
    private fun runCharacter(config: CharacterConfig) {
        var character = accountClient.getCharacter(config.name).data
        log.info("Character details - Name: ${character.name}, Level: ${character.level}, Job: ${config.job}")

        try {
            // Create and run the appropriate job based on the character's job type
            when (config.job.lowercase()) {
                "crafter" -> alchemistJob.run(character)
                "fighter" -> fighterJob.run(character)
                "alchemist" -> alchemistJob.run(character)
                "miner" -> minerJob.run(character)
                "woodworker" -> woodworkerJob.run(character)
                else -> {
                    log.error("Unknown job '${config.job}' for character ${character.name}")
                    throw UnknownJobException(config.job, character.name)
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

    /**
     * Adds an item to the shared thread-safe list.
     * This method can be safely called from multiple threads.
     *
     * @param item The item to add to the shared list
     * @return true if the item was added successfully
     */
    fun addToSharedList(item: Any): Boolean {
        return sharedThreadList.add(item)
    }

    /**
     * Gets a copy of the current contents of the shared thread-safe list.
     * This method returns a new list to avoid concurrent modification issues.
     *
     * @return A copy of the current contents of the shared list
     */
    fun getSharedListContents(): List<Any> {
        synchronized(sharedThreadList) {
            return ArrayList(sharedThreadList)
        }
    }
}
