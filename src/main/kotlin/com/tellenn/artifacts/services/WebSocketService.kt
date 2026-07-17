package com.tellenn.artifacts.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.config.CharacterConfig.Companion.getPredefinedCharacters
import com.tellenn.artifacts.exceptions.MapContentNotFoundException
import com.tellenn.artifacts.models.Event
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import okhttp3.*
import okhttp3.WebSocket
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interface for handling WebSocket messages
 */
interface WebSocketMessageHandler {
    /**
     * Called when a message is received from the WebSocket
     *
     * @param messageType The type of message received
     * @param messageData The message data as a JsonNode
     */
    fun onMessageReceived(messageType: String, messageData: JsonNode)
}

@Service
class WebSocketService(
    private val merchantService: MerchantService,
    private val accountClient: AccountClient,
    private val bankService: BankService,
    private val movementService: MovementService,
    private val gatheringService: GatheringService,
    private val threadService: ThreadService,
    private val battleService: BattleService,
    private val battleSimulatorService: BattleSimulatorService,
    private val monsterService: MonsterService,
    private val characterService: CharacterService,
    private val mapService: MapService,
    private val eventMetrics: EventMetrics,
    private val equipmentService: EquipmentService
) {
    companion object {
        /** Stop an event fight after this many consecutive rounds the fighter could not clear. */
        private const val MAX_EVENT_FIGHT_FAILURES = 3
    }

    val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }
    private val logger = LoggerFactory.getLogger(WebSocketService::class.java)

    @Value("\${artifacts.api.key}")
    private lateinit var apiKey: String

    @Value("\${artifacts.websocket.url}")
    private lateinit var websocketUrl: String

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket connections
        .build()

    private var webSocket: WebSocket? = null

    // Reconnection related fields
    private val isConnecting = AtomicBoolean(false)
    private val reconnectScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val maxReconnectAttempts = 5
    private var reconnectAttempts = 0
    private val initialReconnectDelayMs = 1000L // 1 second
    private val maxReconnectDelayMs = 30000L // 30 seconds

    // Message handlers
    private val messageHandlers = mutableListOf<WebSocketMessageHandler>()

    /**
     * Connects to the Artifacts MMO WebSocket server and subscribes to events.
     *
     * @return true if connection was successful, false otherwise
     */
    fun connect(): Boolean {
        // If already connected, return true
        if (webSocket != null) {
            logger.info("WebSocket already connected")
            return true
        }

        // If already connecting, return true
        if (!isConnecting.compareAndSet(false, true)) {
            logger.info("WebSocket connection already in progress")
            return true
        }

        try {
            // Reset reconnect attempts on manual connect
            reconnectAttempts = 0

            val request = Request.Builder()
                .url(websocketUrl)
                .build()

            val listener = createWebSocketListener()
            webSocket = client.newWebSocket(request, listener)

            logger.debug("WebSocket connection initiated to $websocketUrl")
            return true
        } catch (e: Exception) {
            logger.error("Failed to connect to WebSocket", e)
            isConnecting.set(false)
            return false
        }
    }

    /**
     * Attempts to reconnect to the WebSocket server with exponential backoff.
     */
    private fun scheduleReconnect() {
        if (!isConnecting.compareAndSet(false, true)) {
            logger.info("Reconnection already in progress")
            return
        }

        reconnectAttempts++
        if (reconnectAttempts > maxReconnectAttempts) {
            logger.error("Maximum reconnection attempts reached ($maxReconnectAttempts). Giving up.")
            isConnecting.set(false)
            return
        }

        // Calculate delay with exponential backoff
        val delayMs = Math.min(
            initialReconnectDelayMs * Math.pow(2.0, (reconnectAttempts - 1).toDouble()).toLong(),
            maxReconnectDelayMs
        )

        logger.info("Scheduling reconnection attempt $reconnectAttempts/$maxReconnectAttempts in ${delayMs}ms")

        reconnectScheduler.schedule({
            logger.info("Attempting to reconnect (attempt $reconnectAttempts/$maxReconnectAttempts)")

            try {
                // Clean up any existing connection
                webSocket?.close(1000, "Reconnecting")
                webSocket = null

                // Create a new connection
                val request = Request.Builder()
                    .url(websocketUrl)
                    .build()

                val listener = createWebSocketListener()
                webSocket = client.newWebSocket(request, listener)

                logger.info("Reconnection attempt initiated")
            } catch (e: Exception) {
                logger.error("Failed to reconnect", e)
                isConnecting.set(false)
                // Schedule another reconnection attempt
                scheduleReconnect()
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Disconnects from the WebSocket server.
     */
    fun disconnect() {
        webSocket?.close(1000, "Closing connection")
        webSocket = null
        logger.info("WebSocket disconnected")
    }



    /**
     * Shuts down the WebSocket service and cleans up resources.
     * This should be called when the application is shutting down.
     */
    fun shutdown() {
        logger.info("Shutting down WebSocket service")
        disconnect()
        reconnectScheduler.shutdown()
        try {
            if (!reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                reconnectScheduler.shutdownNow()
            }
        } catch (_: InterruptedException) {
            reconnectScheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
        // Clear all message handlers
        messageHandlers.clear()
        logger.info("WebSocket service shut down")
    }

    /**
     * Creates a WebSocketListener to handle WebSocket events.
     */
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logger.info("WebSocket connection established")
                // Reset reconnection attempts on successful connection
                reconnectAttempts = 0
                isConnecting.set(false)
                sendInitialSubscription(webSocket)
            }

            /*
            Exemple of message :
            {
              'type': 'grandexchange_neworder',
              'data': {"id":"67337920ef441b2c7466d5e8","seller":"muigetsu","code":"copper_ore","quantity":1,"price":1,"created_at":"2024-11-12T15:49:52.343Z"}
            }
             */
            override fun onMessage(webSocket: WebSocket, text: String) {
                logger.info("Received message: $text")

                try {
                    // Parse the message as JSON
                    val jsonNode = objectMapper.readTree(text)

                    // Extract the message type
                    val messageType = jsonNode.path("type").asText("")

                    if (messageType.isNotEmpty()) {
                        logger.debug("Processing message of type: $messageType")

                        recordIncomingEvent(messageType, jsonNode)

                        // Handle interruption directly based on message type
                        when (messageType) {
                            "event_spawn" -> {
                                val event = objectMapper.readValue<Event>(jsonNode.get("data").toString())
                                if (event.map.interactions?.content?.type == "npc"){
                                    val npcCode = event.map.interactions.content.code
                                    logger.info("Merchant spawned: $npcCode")

                                    // Only interrupt Aerith if there is actually something to buy or sell.
                                    // Both eligibility checks are read-only and need no character thread.
                                    val hasPendingPurchases = merchantService.findPendingOneTimePurchases(npcCode).isNotEmpty()
                                    val hasSellableItems = merchantService.findSellableItems(npcCode).isNotEmpty()
                                    if (!hasPendingPurchases && !hasSellableItems) {
                                        logger.info("Nothing to buy from or sell to $npcCode, leaving Aerith on her current task")
                                    } else {
                                        // Execute synchronously in the WebSocket thread with AUTOMATIC priority
                                        // Will not interrupt human-ordered tasks
                                        threadService.executeMissionSync("Aerith", MissionPriority.AUTOMATIC) {
                                            var character = accountClient.getCharacter("Aerith").data
                                            if (hasPendingPurchases) {
                                                character = merchantService.buyOneTimePurchases(character, npcCode)
                                            }
                                            if (hasSellableItems) {
                                                merchantService.sellBankItemTo(character, npcCode)
                                            }
                                        }
                                    }
                                }else if (event.map.interactions?.content?.type == "resource") {
                                    logger.info("Resource spawned: ${event.map.interactions.content.code}")
                                    logger.info("Resource is about ${event.code}")
                                    val characterName = when (event.code) {
                                        "strange_apparition" -> "Kepo"
                                        "magic_apparition" -> "Gustave"
                                        else -> ""
                                    }
                                    logger.info("I want to call ${characterName} to handle it")

                                    // Execute asynchronously with AUTOMATIC priority
                                    // Will not interrupt human-ordered tasks
                                    val canGather = when (event.code) {
                                        "strange_apparition" -> accountClient.getCharacter(characterName).data.miningLevel >= 35
                                        "magic_apparition" -> accountClient.getCharacter(characterName).data.woodcuttingLevel >= 35
                                        else -> false
                                    }
                                    if (!canGather) {
                                        logger.info("${characterName} can't gather ${event.code} yet")
                                    }else {
                                        threadService.assignMissionAsync(characterName, MissionPriority.AUTOMATIC) {
                                            try {
                                                var character = accountClient.getCharacter(characterName).data
                                                do {
                                                    character = movementService.moveToBank(character)
                                                    character = bankService.emptyInventory(character)
                                                    character = gatheringService.craftOrGather(
                                                        character,
                                                        event.map.interactions.content.code,
                                                        character.inventoryMaxItems - 30
                                                    )
                                                } while (true)
                                            } catch (_: MapContentNotFoundException) {
                                                // Resource no longer available
                                            } catch (e: Exception) {
                                                logger.error("Uncaught error occurred while gathering in the event", e)
                                            }
                                        }
                                    }
                                } else if (event.map.interactions?.content?.type == "monster") {
                                    val monsterCode = event.map.interactions.content.code
                                    logger.info("Monster spawned: $monsterCode")

                                    val crafter = accountClient.getCharacter(getPredefinedCharacters().first { it.job == "crafter" }.name).data
                                    val lowestCraftLevel = minOf(crafter.weaponcraftingLevel, crafter.gearcraftingLevel, crafter.jewelrycraftingLevel) / 5 * 5
                                    val monster = monsterService.findMonster(monsterCode)

                                    if (monster.level < lowestCraftLevel) {
                                        logger.info("Monster $monsterCode (level ${monster.level}) is too low — crafter threshold is $lowestCraftLevel, skipping")
                                    } else {
                                        val fighterName = getPredefinedCharacters().first { it.job == "fighter" }.name
                                        val fighter = accountClient.getCharacter(fighterName).data
                                        // On simule le combattant équipé du meilleur stuff disponible en banque
                                        // (comme le fera le combat réel via equipBestAvailableEquipmentForMonsterInBank) :
                                        // sinon on renonce à des monstres gagnables faute d'avoir pris en compte le meilleur loadout.
                                        val equippedFighter = equipmentService.bestEquippedCopyForSimulation(fighter, monsterCode)
                                        // Use the authoritative server-side simulation (as BossFightService/TaskService do).
                                        // The local heuristic over-estimates survivability (unlimited potion healing,
                                        // ignores the `corrupted` resistance-shred effect), which sent under-levelled
                                        // fighters into unwinnable event fights.
                                        val winrate = battleSimulatorService.simulateWithApi(monsterCode, equippedFighter).data.winrate

                                        if (winrate < 100) {
                                            logger.info("$fighterName cannot reliably win against $monsterCode (winrate $winrate%) — skipping")
                                        } else {
                                            logger.info("$fighterName can win against $monsterCode (level ${monster.level}, winrate $winrate%) — assigning fight mission")
                                            threadService.assignMissionAsync(fighterName, MissionPriority.AUTOMATIC) {
                                                fightEventMonster(fighterName, event.map.mapId, monsterCode)
                                            }
                                        }
                                    }
                                }
                            }
                            "event_removed", "grandexchange_sell", "grandexchange_neworder", "achievement_unlocked" -> {
                                logger.info("Message received but not handled: $messageType")
                            }
                        }

                        // Notify all registered handlers (for backward compatibility and other use cases)
                        messageHandlers.forEach { handler ->
                            try {
                                handler.onMessageReceived(messageType, jsonNode)
                            } catch (e: Exception) {
                                logger.error("Error in message handler ${handler.javaClass.simpleName}", e)
                            }
                        }
                    } else {
                        logger.warn("Received message without a type field: $text")
                    }
                } catch (e: Exception) {
                    logger.error("Error processing WebSocket message", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                logger.info("WebSocket closing: $code - $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logger.info("WebSocket closed: $code - $reason")
                // If this wasn't a normal closure, attempt to reconnect
                if (code != 1000) {
                    this@WebSocketService.webSocket = null
                    scheduleReconnect()
                } else {
                    isConnecting.set(false)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logger.error("WebSocket failure", t)
                // Clean up the failed connection
                this@WebSocketService.webSocket = null
                // Schedule a reconnection attempt
                scheduleReconnect()
            }
        }
    }

    /**
     * Compte l'événement reçu pour les métriques, en excluant les messages Grand Exchange
     * (ventes/achats, préfixe `grandexchange`) qui ne sont pas des événements de jeu. Le code
     * est lu de façon générique dans le payload (`data.code`) et vaut "-" s'il est absent.
     */
    internal fun recordIncomingEvent(messageType: String, root: JsonNode) {
        if (messageType.startsWith("grandexchange")) {
            return
        }
        val code = root.path("data").path("code").asText("-").ifEmpty { "-" }
        eventMetrics.recordEvent(messageType, code)
    }

    /**
     * Fights an event monster repeatedly, emptying the inventory at the bank between rounds.
     *
     * Stops when the monster disappears, or after [MAX_EVENT_FIGHT_FAILURES] consecutive rounds the
     * fighter could not clear. A round that ends without a full inventory means the fight could not be
     * sustained (battle lost or interrupted): without this cap the loop retried a losing fight forever,
     * even after the event had expired.
     */
    internal fun fightEventMonster(fighterName: String, mapId: Int, monsterCode: String) {
        var consecutiveFailures = 0
        try {
            var character = accountClient.getCharacter(fighterName).data
            do {
                character = movementService.moveToBank(character)
                character = bankService.emptyInventory(character)

                // The event may have ended: the cell is then empty or occupied by another monster
                // we must not fight. Stop unless our target monster is still there.
                if (!mapService.isMonsterPresentAt(mapId, monsterCode)) {
                    logger.info("$monsterCode is no longer on map $mapId (event ended) — $fighterName stops")
                    return
                }

                character = movementService.moveCharacterToCell(mapId, character)
                character = battleService.battleUntilInvIsFull(character, monsterCode)
                // It's possible that the monster has moved away from the cell and was replaced, but that's ok.
                if (characterService.isInventoryFull(character)) {
                    consecutiveFailures = 0
                } else {
                    consecutiveFailures++
                    logger.warn("$fighterName could not clear $monsterCode (failure $consecutiveFailures/$MAX_EVENT_FIGHT_FAILURES)")
                }
            } while (consecutiveFailures < MAX_EVENT_FIGHT_FAILURES)

            logger.info("$fighterName stops fighting $monsterCode after $MAX_EVENT_FIGHT_FAILURES consecutive failures")
        } catch (_: MapContentNotFoundException) {
            logger.info("Monster $monsterCode is no longer available")
        } catch (e: Exception) {
            logger.error("Uncaught error occurred while fighting event monster $monsterCode", e)
        }
    }

    /**
     * Sends the initial subscription message with the API token.
     */
    private fun sendInitialSubscription(webSocket: WebSocket) {
        try {
            val subscriptionMessage = mapOf(
                "token" to apiKey,
                "subscriptions" to listOf(
                    "event_spawn",
                    "event_removed",
                    "grandexchange_sell",
                    "grandexchange_sell_order",
                    "grandexchange_buy",
                    "grandexchange_buy_order",
                    "achievement_unlocked"
                )
            )

            val jsonMessage = objectMapper.writeValueAsString(subscriptionMessage)
            webSocket.send(jsonMessage)

            logger.debug("Sent initial subscription message")
        } catch (e: Exception) {
            logger.error("Failed to send initial subscription", e)
        }
    }
}
