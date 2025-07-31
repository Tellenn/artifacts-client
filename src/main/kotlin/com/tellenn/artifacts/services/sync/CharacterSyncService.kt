package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.config.CharacterConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CharacterSyncService(
    private val accountClient: AccountClient,
    private val serverVersionService: ServerVersionService
) {
    private val logger = LoggerFactory.getLogger(CharacterSyncService::class.java)

    // Map to store character configurations and their corresponding character objects
    private val characterMap = mutableMapOf<CharacterConfig, ArtifactsCharacter>()

    /**
     * Ensures that all predefined characters from the configuration exist.
     * If a character doesn't exist, it will be created.
     * Returns a map of character configurations to character objects.
     *
     * @param forceSync Whether to force the sync regardless of server version (default: false)
     * @return Map of CharacterConfig to ArtifactsCharacter
     */
    fun syncPredefinedCharacters(forceSync: Boolean = false): Map<CharacterConfig, ArtifactsCharacter> {
        logger.debug("Starting character sync process")

        // Clear the existing map
        characterMap.clear()

        // Get predefined characters from config
        val predefinedCharacters = CharacterConfig.getPredefinedCharacters()
        logger.debug("Found ${predefinedCharacters.size} predefined characters in config")

        // Process each predefined character
        for (characterConfig in predefinedCharacters) {
            try {
                // Try to get the character
                val response = accountClient.getCharacter(characterConfig.name)

                if (response.data != null) {
                    // Character exists, add to map
                    logger.debug("Character ${characterConfig.name} already exists")
                    characterMap[characterConfig] = response.data
                } else {
                    // Character doesn't exist, create it
                    logger.warn("Character ${characterConfig.name} doesn't exist, creating...")
                    val createResponse = accountClient.createCharacter(
                        name = characterConfig.name,
                        skin = characterConfig.skin
                    )

                    if (createResponse.data != null) {
                        // Character created successfully, add to map
                        logger.debug("Character ${characterConfig.name} created successfully")
                        characterMap[characterConfig] = createResponse.data
                    } else {
                        // Failed to create character
                        logger.debug("Failed to create character ${characterConfig.name}")
                    }
                }
            } catch (e: Exception) {
                logger.error("Error processing character ${characterConfig.name}", e)
            }
        }

        // Save the server version after successful sync
        serverVersionService.updateServerVersion()
        logger.info("Character sync completed and server version updated. Total characters synced: ${characterMap.size}")
        return characterMap
    }

    /**
     * Gets the current map of character configurations to character objects.
     *
     * @return Map of CharacterConfig to ArtifactsCharacter
     */
    fun getCharacterMap(): Map<CharacterConfig, ArtifactsCharacter> {
        return characterMap
    }
}