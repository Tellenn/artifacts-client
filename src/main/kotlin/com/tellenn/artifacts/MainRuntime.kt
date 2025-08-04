package com.tellenn.artifacts

import com.fasterxml.jackson.databind.ObjectMapper
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.clients.ServerStatusClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.config.CharacterConfig
import com.tellenn.artifacts.exceptions.UnknownJobException
import com.tellenn.artifacts.jobs.AlchemistJob
import com.tellenn.artifacts.jobs.CrafterJob
import com.tellenn.artifacts.jobs.FighterJob
import com.tellenn.artifacts.jobs.MinerJob
import com.tellenn.artifacts.jobs.WoodworkerJob
import com.tellenn.artifacts.services.sync.BankItemSyncService
import com.tellenn.artifacts.services.sync.CharacterSyncService
import com.tellenn.artifacts.services.sync.ItemSyncService
import com.tellenn.artifacts.services.sync.MapSyncService
import com.tellenn.artifacts.services.sync.MonsterSyncService
import com.tellenn.artifacts.services.sync.ResourceSyncService
import com.tellenn.artifacts.services.sync.ServerVersionService
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

        log.debug(objectMapper.writeValueAsString(AppConfig))
        
        // Get command line arguments
        val forceSync = args?.containsOption("force-sync") ?: false
        log.debug("Force sync: $forceSync")
        
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
                var charThread = CharacterThread(config, crafterJob, fighterJob, alchemistJob, minerJob, woodworkerJob)
                webSocketService.addCharacterThread(config.name, charThread)
                charThread.startThread()
            } catch (e: Exception) {
                log.error("Exception occurred while starting thread for character: ${character.name}", e)

                // Wait a moment before attempting to restart
                try {
                    log.info("Attempting to restart thread for character: ${character.name}")
                    Thread.sleep(1000) // Wait 1 second before restarting

                    // Create a new thread and start it
                    var charThread = CharacterThread(config, crafterJob, fighterJob, alchemistJob, minerJob, woodworkerJob)
                    webSocketService.addCharacterThread(config.name, charThread)
                    charThread.startThread()

                    log.info("Successfully restarted thread for character: ${character.name}")
                } catch (e2: Exception) {
                    log.error("Failed to restart thread for character: ${character.name}", e2)
                }
            }
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
