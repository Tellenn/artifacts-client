package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

/**
 * Service for managing character-related operations.
 * Provides functionality for character resting and other character-related actions.
 */
@Service
class CharacterService(
    private val characterClient: CharacterClient
) {
    private val log = LogManager.getLogger(CharacterService::class.java)

    /**
     * Makes a character rest to recover HP.
     * If the character is already at full HP, no rest is performed.
     *
     * @param character The character to rest
     * @return The updated character if rested, or the original character if already at full HP
     */
    fun rest(character: ArtifactsCharacter): ArtifactsCharacter {
        // Check if character is already at full HP
        if (character.hp >= character.maxHp) {
            return character
        }

        // Character needs to rest
        val response = characterClient.rest(character.name)
        
        // Return the updated character
        return response.data.character
    }
}