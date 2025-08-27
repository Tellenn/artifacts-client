package com.tellenn.artifacts.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.CharacterThread
import com.tellenn.artifacts.MainRuntime
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.config.CharacterConfig
import com.tellenn.artifacts.config.CharacterConfig.Companion.getCharacterByName
import com.tellenn.artifacts.config.CharacterConfig.Companion.getPredefinedCharacters
import com.tellenn.artifacts.models.Event
import com.tellenn.artifacts.services.sync.CharacterSyncService
import okhttp3.*
import okhttp3.WebSocket
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
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
) {
    val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }
    private val logger = LoggerFactory.getLogger(WebSocketService::class.java)

    @Value("\${artifacts.api.key}")
    private lateinit var apiKey: String

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

    // Map to store character threads by character name
    private val characterThreads = ConcurrentHashMap<String, CharacterThread>()

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
                .url("wss://realtime.artifactsmmo.com")
                .build()

            val listener = createWebSocketListener()
            webSocket = client.newWebSocket(request, listener)

            logger.debug("WebSocket connection initiated")
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
                    .url("wss://realtime.artifactsmmo.com")
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
     * Registers a message handler to receive WebSocket messages.
     * 
     * @param handler The handler to register
     */
    fun registerMessageHandler(handler: WebSocketMessageHandler) {
        messageHandlers.add(handler)
        logger.info("Registered WebSocket message handler: ${handler.javaClass.simpleName}")
    }

    /**
     * Unregisters a message handler.
     * 
     * @param handler The handler to unregister
     * @return true if the handler was removed, false if it wasn't registered
     */
    fun unregisterMessageHandler(handler: WebSocketMessageHandler): Boolean {
        val removed = messageHandlers.remove(handler)
        if (removed) {
            logger.info("Unregistered WebSocket message handler: ${handler.javaClass.simpleName}")
        }
        return removed
    }

    /**
     * Adds a character thread to the map.
     *
     * @param characterName The name of the character
     * @param thread The thread to add
     * @return The previous thread associated with the character, or null if there was none
     */
    fun addCharacterThread(characterName: String, thread: CharacterThread): CharacterThread? {
        logger.debug("Adding thread for character: $characterName")
        return characterThreads.put(characterName, thread)
    }

    /**
     * Gets a character thread from the map.
     *
     * @param characterName The name of the character
     * @return The thread associated with the character, or null if there is none
     */
    fun getCharacterThread(characterName: String): CharacterThread? {
        return characterThreads[characterName]
    }

    /**
     * Interrupts a specific character thread.
     *
     * @param characterName The name of the character whose thread should be interrupted
     * @return true if the thread was interrupted, false if the thread doesn't exist
     */
    fun interruptCharacterThread(characterName: String): Boolean {
        val characterThread = characterThreads[characterName] ?: return false

        logger.info("Interrupting thread for character: $characterName")
        characterThread.thread.interrupt()
        return true
    }

    /**
     * Interrupts a specific character thread.
     *
     * @param characterName The name of the character whose thread should be interrupted
     * @return true if the thread was interrupted, false if the thread doesn't exist
     */
    fun restartCharacterThread(characterName: String): Boolean {
        val characterThread = characterThreads[characterName] ?: return false
        characterThread.startThread()
        logger.info("Restarting thread for character: $characterName")
        return true
    }

    /**
     * Interrupts all character threads.
     *
     * @return The number of threads that were interrupted
     */
    fun interruptAllCharacterThreads(): Int {
        logger.info("Interrupting all character threads")
        var count = 0
/*
        characterThreads.forEach { (characterName, thread) ->
            thread.interrupt()
            logger.info("Interrupted thread for character: $characterName")
            count++
        }*/

        return count
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
        } catch (e: InterruptedException) {
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

                        // Handle interruption directly based on message type
                        when (messageType) {
                            "event_spawn" -> {
                                val event = objectMapper.readValue<Event>(jsonNode.get("data").toString())
                                if (event.map.content?.type == "npc"){

                                    logger.info("!!!!!!!! Merchant spawned: ${event.map.content.code}")
                                    interruptCharacterThread("Aerith")
                                    logger.info("!!!!!!!! Interrupted the thread of Aerith")

                                    var character = accountClient.getCharacter("Aerith").data
                                    merchantService.sellBankItemTo(character, event.map.content.code)
                                    restartCharacterThread("Aerith"
                                    )
                                }else if (event.map.content?.type == "resource"){
                                    logger.info("Resource spawned: ${event.map.content.code}")
                                    // TODO : Gather resource until event is over

                                }else if (event.map.content?.type == "monster"){
                                    logger.info("Monster spawned: ${event.map.content.code}")
                                    // TODO : Fight if it's interesting or that you can
                                }
                            }
                            "event_removed", "grandexchange_sell", "grandexchange_neworder", "achievement_unlocked" -> {

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
                    "grandexchange_neworder", 
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
