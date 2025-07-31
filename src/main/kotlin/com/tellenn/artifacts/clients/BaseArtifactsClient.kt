package com.tellenn.artifacts.clients

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.exceptions.*
import com.tellenn.artifacts.services.ClientErrorService
import com.tellenn.artifacts.services.ws.MessageService
import lombok.extern.slf4j.Slf4j
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Slf4j
@Component
abstract class BaseArtifactsClient() {

    private val log = LoggerFactory.getLogger(BaseArtifactsClient::class.java)

    @Autowired
    private lateinit var clientErrorService: ClientErrorService

    @Autowired
    private lateinit var messageService : MessageService

    @Value("\${artifacts.api.url}")
    lateinit var url: String

    @Value("\${artifacts.api.key}")
    lateinit var key: String

    val client = OkHttpClient()

    val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    /**
     * Checks if the response contains character information and sends it to the websocket if it does.

     * @param responseBody The response body as a string
     */
    private fun handleCharacter(responseBody: String) {
        try {
            val response = objectMapper.readValue<Map<String, Any>>(responseBody)
            val data = response["data"] as? Map<*, *> ?: return
            val character = data["character"] as? Map<*, *> ?: return
            if(character.isEmpty()) return
            messageService.sendCharacterMessage(character.toString());
        } catch (e: Exception) {
            log.warn("Failed to parse cooldown information: ${e.message}")
        }
    }

    /**
     * Checks if the response contains cooldown information and sleeps the thread if it does.
     * This handles cooldown information in the data field of successful responses.
     * 
     * For 499 error responses with cooldown information in the error field, see the special handling
     * in sendGetRequest and sendPostRequest methods.
     * 
     * @param responseBody The response body as a string
     */
    private fun handleCooldown(responseBody: String) {
        try {
            val response = objectMapper.readValue<Map<String, Any>>(responseBody)
            val data = response["data"] as? Map<String, Any> ?: return
            val cooldown = data["cooldown"] as? Map<String, Any> ?: return

            val remainingSeconds = cooldown["remaining_seconds"] as? Int ?: return
            if (remainingSeconds > 0) {
                log.debug("Cooldown detected: $remainingSeconds seconds. Sleeping thread...")
                Thread.sleep(remainingSeconds * 1000L)
                log.debug("Thread resumed after cooldown")
            }
        } catch (e: Exception) {
            log.warn("Failed to parse cooldown information: ${e.message}")
        }
    }

    /**
     * Maps a response code to the appropriate exception.
     * 
     * @param code The response code
     * @param message The error message
     * @return The appropriate exception for the response code
     */
    private fun mapResponseCodeToException(code: Int, message: String): ArtifactsApiException {
        return when (code) {
            // General
            ErrorCodes.INVALID_PAYLOAD -> InvalidPayloadException(message)
            ErrorCodes.TOO_MANY_REQUESTS -> TooManyRequestsException(message)
            ErrorCodes.NOT_FOUND -> NotFoundException(message)
            ErrorCodes.FATAL_ERROR -> FatalErrorException(message)

            // Email token error codes
            ErrorCodes.INVALID_EMAIL_RESET_TOKEN -> InvalidEmailResetTokenException(message)
            ErrorCodes.EXPIRED_EMAIL_RESET_TOKEN -> ExpiredEmailResetTokenException(message)
            ErrorCodes.USED_EMAIL_RESET_TOKEN -> UsedEmailResetTokenException(message)

            // Account Error Codes
            ErrorCodes.TOKEN_INVALID -> TokenInvalidException(message)
            ErrorCodes.TOKEN_EXPIRED -> TokenExpiredException(message)
            ErrorCodes.TOKEN_MISSING -> TokenMissingException(message)
            ErrorCodes.TOKEN_GENERATION_FAIL -> TokenGenerationFailException(message)
            ErrorCodes.USERNAME_ALREADY_USED -> UsernameAlreadyUsedException(message)
            ErrorCodes.EMAIL_ALREADY_USED -> EmailAlreadyUsedException(message)
            ErrorCodes.SAME_PASSWORD -> SamePasswordException(message)
            ErrorCodes.CURRENT_PASSWORD_INVALID -> CurrentPasswordInvalidException(message)
            ErrorCodes.ACCOUNT_NOT_MEMBER -> AccountNotMemberException(message)
            ErrorCodes.ACCOUNT_SKIN_NOT_OWNED -> AccountSkinNotOwnedException(message)

            // Character Error Codes
            ErrorCodes.CHARACTER_NOT_ENOUGH_HP -> CharacterNotEnoughHpException(message)
            ErrorCodes.CHARACTER_MAXIMUM_UTILITES_EQUIPED -> CharacterMaximumUtilitiesEquippedException(message)
            ErrorCodes.CHARACTER_ITEM_ALREADY_EQUIPED -> CharacterItemAlreadyEquippedException(message)
            ErrorCodes.CHARACTER_LOCKED -> CharacterLockedException(message)
            ErrorCodes.CHARACTER_NOT_THIS_TASK -> CharacterNotThisTaskException(message)
            ErrorCodes.CHARACTER_TOO_MANY_ITEMS_TASK -> CharacterTooManyItemsTaskException(message)
            ErrorCodes.CHARACTER_NO_TASK -> CharacterNoTaskException(message)
            ErrorCodes.CHARACTER_TASK_NOT_COMPLETED -> CharacterTaskNotCompletedException(message)
            ErrorCodes.CHARACTER_ALREADY_TASK -> CharacterAlreadyTaskException(message)
            ErrorCodes.CHARACTER_ALREADY_MAP -> CharacterAlreadyMapException(message)
            ErrorCodes.CHARACTER_SLOT_EQUIPMENT_ERROR -> CharacterSlotEquipmentErrorException(message)
            ErrorCodes.CHARACTER_GOLD_INSUFFICIENT -> CharacterGoldInsufficientException(message)
            ErrorCodes.CHARACTER_NOT_SKILL_LEVEL_REQUIRED -> CharacterNotSkillLevelRequiredException(message)
            ErrorCodes.CHARACTER_NAME_ALREADY_USED -> CharacterNameAlreadyUsedException(message)
            ErrorCodes.MAX_CHARACTERS_REACHED -> MaxCharactersReachedException(message)
            ErrorCodes.CHARACTER_CONDITION_NOT_MET -> CharacterConditionNotMetException(message)
            ErrorCodes.CHARACTER_INVENTORY_FULL -> CharacterInventoryFullException(message)
            ErrorCodes.CHARACTER_NOT_FOUND -> CharacterNotFoundException(message)
            ErrorCodes.CHARACTER_IN_COOLDOWN -> CharacterInCooldownException(message)

            // Item Error Codes
            ErrorCodes.ITEM_INSUFFICIENT_QUANTITY -> ItemInsufficientQuantityException(message)
            ErrorCodes.ITEM_INVALID_EQUIPMENT -> ItemInvalidEquipmentException(message)
            ErrorCodes.ITEM_RECYCLING_INVALID_ITEM -> ItemRecyclingInvalidItemException(message)
            ErrorCodes.ITEM_INVALID_CONSUMABLE -> ItemInvalidConsumableException(message)
            ErrorCodes.MISSING_ITEM -> MissingItemException(message)

            // Grand Exchange Error Codes
            ErrorCodes.GE_MAX_QUANTITY -> GEMaxQuantityException(message)
            ErrorCodes.GE_NOT_IN_STOCK -> GENotInStockException(message)
            ErrorCodes.GE_NOT_THE_PRICE -> GENotThePriceException(message)
            ErrorCodes.GE_TRANSACTION_IN_PROGRESS -> GETransactionInProgressException(message)
            ErrorCodes.GE_NO_ORDERS -> GENoOrdersException(message)
            ErrorCodes.GE_MAX_ORDERS -> GEMaxOrdersException(message)
            ErrorCodes.GE_TOO_MANY_ITEMS -> GETooManyItemsException(message)
            ErrorCodes.GE_SAME_ACCOUNT -> GESameAccountException(message)
            ErrorCodes.GE_INVALID_ITEM -> GEInvalidItemException(message)
            ErrorCodes.GE_NOT_YOUR_ORDER -> GENotYourOrderException(message)

            // Bank Error Codes
            ErrorCodes.BANK_INSUFFICIENT_GOLD -> BankInsufficientGoldException(message)
            ErrorCodes.BANK_TRANSACTION_IN_PROGRESS -> BankTransactionInProgressException(message)
            ErrorCodes.BANK_FULL -> BankFullException(message)

            // Maps Error Codes
            ErrorCodes.MAP_NOT_FOUND -> MapNotFoundException(message)
            ErrorCodes.MAP_CONTENT_NOT_FOUND -> MapContentNotFoundException(message)

            // NPC Error Codes
            ErrorCodes.NPC_NOT_FOR_SALE -> NPCNotForSaleException(message)
            ErrorCodes.NPC_NOT_FOR_BUY -> NPCNotForBuyException(message)

            // Default
            else -> ArtifactsApiException(code, message)
        }
    }

    /**
     * Sends a GET request to the specified path.
     * 
     * This method handles 499 (CHARACTER_IN_COOLDOWN) errors by:
     * 1. Parsing the error response to extract the cooldown time
     * 2. Sleeping for the remaining cooldown time
     * 3. Automatically retrying the request after the cooldown period
     * 
     * @param path The path to send the request to
     * @return The response from the server
     */
    fun sendGetRequest(path : String) : Response {
        val getRequest = Request.Builder()
            .url(url+path)
            .header("Authorization", "Bearer $key")
            .build()

        val clientType = this.javaClass.simpleName
        val requestMethod = "GET"
        val requestParams = path
        val requestBody = null

        try {
            val response = client.newCall(getRequest).execute()
            if (!response.isSuccessful) {
                // Get the response body as a string for error logging
                val responseBodyString = response.body?.string() ?: ""

                // Check if this is a 499 cooldown error
                if (response.code == ErrorCodes.CHARACTER_IN_COOLDOWN) {
                    try {
                        // Parse the error response to extract the cooldown time
                        val errorResponse = objectMapper.readValue<Map<String, Any>>(responseBodyString)
                        val error = errorResponse["error"] as? Map<String, Any>
                        val message = error?.get("message") as? String
                        
                        if (message != null && message.contains("cooldown")) {
                            // Extract the cooldown time from the message
                            val regex = """(\d+\.\d+|\d+) seconds remaining""".toRegex()
                            val matchResult = regex.find(message)
                            val cooldownSeconds = matchResult?.groupValues?.get(1)?.toDoubleOrNull()
                            
                            if (cooldownSeconds != null && cooldownSeconds > 0) {
                                log.debug("Character in cooldown: $cooldownSeconds seconds remaining. Sleeping thread...")
                                // Sleep for the cooldown time (convert to milliseconds)
                                Thread.sleep((cooldownSeconds * 1000).toLong())
                                log.debug("Thread resumed after cooldown. Retrying request...")
                                
                                // Retry the request
                                return sendGetRequest(path)
                            }
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to parse cooldown information: ${e.message}")
                    }
                }
                logAndThrowError(response, clientType, path, requestMethod, requestParams, requestBody, responseBodyString)
            }

            // Get the response body as a string
            val responseBodyString = response.body?.string() ?: ""

            // Check for cooldown in the response
            handleCooldown(responseBodyString)
            handleCharacter(responseBodyString)

            // Create a new response with the same body since we've consumed it
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val responseBody = responseBodyString.toByteArray().let { 
                okhttp3.ResponseBody.create(mediaType, it) 
            }

            return response.newBuilder()
                .body(responseBody)
                .build()
        } catch (e: Exception) {
            // Log any other exceptions that might occur
            if (e !is ArtifactsApiException) {  // Only log if not already logged as an API exception
                logAndThrowError(null, clientType, path, requestMethod, requestParams, requestBody, "")
            }
            throw e
        }
    }

    /**
     * Sends a POST request to the specified path with the given body.
     * 
     * This method handles 499 (CHARACTER_IN_COOLDOWN) errors by:
     * 1. Parsing the error response to extract the cooldown time
     * 2. Sleeping for the remaining cooldown time
     * 3. Automatically retrying the request after the cooldown period
     * 
     * @param path The path to send the request to
     * @param body The request body as a JSON string
     * @return The response from the server
     */
    fun sendPostRequest(path : String, body: String) : Response {
        val postBody = body.trimIndent()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = postBody.toRequestBody(mediaType)

        val postRequest = Request.Builder()
            .url(url+path)
            .post(requestBody)
            .header("Authorization", "Bearer $key")
            .build()

        val clientType = this.javaClass.simpleName
        val requestMethod = "POST"
        val requestParams = path

        try {
            val response = client.newCall(postRequest).execute()
            if (!response.isSuccessful) {
                // Get the response body as a string for error logging
                val responseBodyString = response.body?.string() ?: ""

                // Check if this is a 499 cooldown error
                if (response.code == ErrorCodes.CHARACTER_IN_COOLDOWN) {
                    try {
                        // Parse the error response to extract the cooldown time
                        val errorResponse = objectMapper.readValue<Map<String, Any>>(responseBodyString)
                        val error = errorResponse["error"] as? Map<String, Any>
                        val message = error?.get("message") as? String
                        
                        if (message != null && message.contains("cooldown")) {
                            // Extract the cooldown time from the message
                            val regex = """(\d+\.\d+|\d+) seconds remaining""".toRegex()
                            val matchResult = regex.find(message)
                            val cooldownSeconds = matchResult?.groupValues?.get(1)?.toDoubleOrNull()
                            
                            if (cooldownSeconds != null && cooldownSeconds > 0) {
                                log.debug("Character in cooldown: $cooldownSeconds seconds remaining. Sleeping thread...")
                                // Sleep for the cooldown time (convert to milliseconds)
                                Thread.sleep((cooldownSeconds * 1000).toLong())
                                log.debug("Thread resumed after cooldown. Retrying request...")
                                
                                // Retry the request
                                return sendPostRequest(path, body)
                            }
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to parse cooldown information: ${e.message}")
                    }
                }
                try{

                    throw mapResponseCodeToException(response.code, "Request failed with status code ${response.code}")
                }catch (e: ArtifactsApiException){
                    // Log the error to the database
                    logAndThrowError(response, clientType, path, requestMethod, body, null, responseBodyString)
                }



            }

            // Get the response body as a string
            val responseBodyString = response.body?.string() ?: ""

            // Check for cooldown in the response
            handleCooldown(responseBodyString)
            handleCharacter(responseBodyString)

            // Log the response
            log.debug("Réponse POST: $responseBodyString")

            // Create a new response with the same body since we've consumed it
            val responseBody = responseBodyString.toByteArray().let { 
                okhttp3.ResponseBody.create(mediaType, it) 
            }

            return response.newBuilder()
                .body(responseBody)
                .build()
        } catch (e: Exception) {
            // Log any other exceptions that might occur
            if (e !is ArtifactsApiException) {  // Only log if not already logged as an API exception
                logAndThrowError(null, clientType, path, requestMethod, requestParams, null, "")
            }
            throw e
        }
    }

    private fun logAndThrowError(
        response: Response?,
        clientType: String,
        path: String,
        requestMethod: String,
        requestParams: String,
        requestBody: Nothing?,
        responseBodyString: String
    ): Nothing {
        try {
            throw mapResponseCodeToException(response?.code ?: 999, "Request failed with status code ${response?.code ?: 999}")
        } catch (e: ArtifactsApiException) {

            var character: ArtifactsCharacter? = null
            if(path.contains("Kepo")){
                character = getCharacterForError("Kepo").data
            } else if(path.contains("Renoir")){
                character = getCharacterForError("Renoir").data
            }else if(path.contains("Gustave")){
                character = getCharacterForError("Gustave").data
            }else if(path.contains("Cloud")){
                character = getCharacterForError("Cloud").data
            }else if(path.contains("Aerith")){
                character = getCharacterForError("Aerith").data
            }
            clientErrorService.logError(
                clientType = clientType,
                endpoint = path,
                requestMethod = requestMethod,
                requestParams = requestParams,
                requestBody = requestBody,
                responseBody = responseBodyString,
                errorCode = response?.code ?: 999,
                errorMessage = "Request failed with status code ${response?.code ?: 999}",
                character = character,
                stackTrace = e.stackTraceToString()
            )
            throw e
        }
    }

    private fun getCharacterForError(name: String): ArtifactsResponseBody<ArtifactsCharacter> {
        return sendGetRequest("/characters/$name").use { response ->
            val responseBody = response.body!!.string()
            objectMapper.readValue<ArtifactsResponseBody<ArtifactsCharacter>>(responseBody)
        }
    }
}
