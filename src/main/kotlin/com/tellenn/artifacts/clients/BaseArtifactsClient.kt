package com.tellenn.artifacts.clients

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.utils.TimeUtils
import com.tellenn.artifacts.config.CharacterConfig
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.exceptions.*
import com.tellenn.artifacts.services.ClientErrorService
import com.tellenn.artifacts.services.ws.MessageService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InterruptedIOException
import java.lang.Thread.sleep
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
abstract class BaseArtifactsClient(deps: BaseClientDependencies) {

    private val log = LoggerFactory.getLogger(BaseArtifactsClient::class.java)
    private val clientErrorService: ClientErrorService = deps.clientErrorService
    private val messageService: MessageService = deps.messageService
    private val timeUtils: TimeUtils = deps.timeUtils
    val url: String = deps.url
    private val key: String = deps.key

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    companion object {
        private val cooldowns = ConcurrentHashMap<String, Instant>()
    }

    private fun handleCharacter(responseBody: String) {
        try {
            val response = objectMapper.readValue<Map<String, Any>>(responseBody)
            val data = response["data"] as? Map<*, *> ?: return
            val character = data["character"] as? Map<*, *> ?: return
            if (character.isEmpty()) return
            messageService.sendCharacterMessage(character.toString())
        } catch (e: Exception) {
            log.warn("Failed to parse character information: ${e.message}")
        }
    }

    private fun handleCooldown(responseBody: String) {
        try {
            val characterNames = CharacterConfig.getPredefinedCharacters().joinToString("|") { it.name }
            val matchResult = characterNames.toRegex().find(responseBody) ?: return

            val cooldownFromRegex = """\s*"cooldown_expiration"\s*:\s*"([^"]+)"""".toRegex()
                .find(responseBody)?.groupValues?.get(1) ?: return

            matchResult.groupValues.forEach { cooldowns[it] = Instant.parse(cooldownFromRegex) }
        } catch (e: Exception) {
            log.error("Failed to parse cooldown information: ${e.message}")
        }
    }

    private fun handle499Cooldown(responseBody: String) {
        try {
            val cooldownSeconds = """(\d+\.\d+|\d+) seconds remaining""".toRegex()
                .find(responseBody)?.groupValues?.get(1)?.toDoubleOrNull()
            if (cooldownSeconds != null && cooldownSeconds > 0) {
                log.debug("Character in cooldown: $cooldownSeconds seconds remaining. Sleeping thread...")
                sleep((cooldownSeconds * 1000).toLong())
                log.debug("Thread resumed after cooldown.")
            }
        } catch (e: Exception) {
            log.error("Failed to parse cooldown information: ${e.message}")
        }
    }

    fun waitForCooldown(characterName: String) {
        val expiration = cooldowns[characterName] ?: return
        val now = timeUtils.now()
        if (now.isBefore(expiration)) {
            val sleepMillis = java.time.Duration.between(now, expiration).toMillis()
            if (sleepMillis > 0) {
                log.debug("Pre-emptive cooldown for $characterName: sleeping $sleepMillis ms")
                sleep(sleepMillis)
            }
        }
    }

    private fun checkRateLimit(response: Response, period: String, retryFn: () -> Response): Response? {
        if (response.headers["x-ratelimit-remaining-$period"] != "0") return null
        response.headers["x-ratelimit-reset-$period"]?.toLongOrNull()?.let { resetTimestamp ->
            val sleepSeconds = resetTimestamp - System.currentTimeMillis() / 1000
            if (sleepSeconds > 0) {
                log.error("$period rate limit reached for ${javaClass.simpleName}, sleeping for $sleepSeconds seconds")
                sleep(sleepSeconds * 1000)
            }
        }
        response.close()
        return retryFn()
    }

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

            else -> ArtifactsApiException(code, message)
        }
    }

    fun sendGetRequest(path: String, retried: Boolean = false): Response {
        val request = Request.Builder()
            .url(url + path)
            .header("Authorization", "Bearer $key")
            .build()
        val clientType = javaClass.simpleName

        try {
            val response = client.newCall(request).execute()
            checkRateLimit(response, "hour") { sendGetRequest(path) }?.let { return it }
            checkRateLimit(response, "minute") { sendGetRequest(path) }?.let { return it }
            checkRateLimit(response, "second") { sendGetRequest(path) }?.let { return it }

            if (!response.isSuccessful) {
                val responseBodyString = response.body?.string() ?: ""
                if (response.code == ErrorCodes.CHARACTER_IN_COOLDOWN) {
                    handle499Cooldown(responseBodyString)
                    log.debug("Thread resumed after cooldown. Retrying request...")
                    return sendGetRequest(path)
                }
                if (response.code == 502 && !retried) {
                    response.close()
                    log.warn("502 Bad Gateway on GET $path, retrying once...")
                    return sendGetRequest(path, retried = true)
                }
                logAndThrowError(response, clientType, path, "GET", path, null, responseBodyString)
            }

            val responseBodyString = response.body?.string() ?: ""
            handleCooldown(responseBodyString)
            handleCharacter(responseBodyString)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val responseBody = responseBodyString.toByteArray().toResponseBody(mediaType)
            return response.newBuilder().body(responseBody).build()
        } catch (e: Exception) {
            if (e !is ArtifactsApiException && e !is InterruptedIOException && e !is InterruptedException) {
                logAndThrowError(null, clientType, path, "GET", path, null, "")
            }
            throw e
        }
    }

    fun sendPostRequest(path: String, body: String, retried: Boolean = false): Response {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = body.trimIndent().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url + path)
            .post(requestBody)
            .header("Authorization", "Bearer $key")
            .build()
        val clientType = javaClass.simpleName

        try {
            val response = client.newCall(request).execute()
            checkRateLimit(response, "hour") { sendPostRequest(path, body) }?.let { return it }
            checkRateLimit(response, "minute") { sendPostRequest(path, body) }?.let { return it }
            checkRateLimit(response, "second") { sendPostRequest(path, body) }?.let { return it }

            if (!response.isSuccessful) {
                val responseBodyString = response.body?.string() ?: ""
                if (response.code == ErrorCodes.CHARACTER_IN_COOLDOWN) {
                    handle499Cooldown(responseBodyString)
                    log.debug("Thread resumed after cooldown. Retrying request...")
                    return sendPostRequest(path, body)
                }
                if (response.code == 502 && !retried) {
                    response.close()
                    log.warn("502 Bad Gateway on POST $path, retrying once...")
                    return sendPostRequest(path, body, retried = true)
                }
                try {
                    throw mapResponseCodeToException(response.code, "Request failed with status code ${response.code}")
                } catch (_: ArtifactsApiException) {
                    logAndThrowError(response, clientType, path, "POST", body, null, responseBodyString)
                }
            }

            val responseBodyString = response.body?.string() ?: ""
            handleCooldown(responseBodyString)
            handleCharacter(responseBodyString)
            log.debug("Réponse POST: $responseBodyString")

            val responseBody = responseBodyString.toByteArray().toResponseBody(mediaType)
            return response.newBuilder().body(responseBody).build()
        } catch (e: Exception) {
            if (e !is ArtifactsApiException && e !is InterruptedIOException && e !is InterruptedException) {
                logAndThrowError(null, clientType, path, "POST", path, null, "")
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
            throw mapResponseCodeToException(
                response?.code ?: 999,
                "Request failed with status code ${response?.code ?: 999}"
            )
        } catch (e: ArtifactsApiException) {
            log.error("We have an issue with the request at $path", e)
            val character: ArtifactsCharacter? = CharacterConfig.getPredefinedCharacters()
                .firstOrNull { path.contains(it.name) }
                ?.let { getCharacterForError(it.name).data }
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