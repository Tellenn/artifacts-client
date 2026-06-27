package com.tellenn.artifacts.services

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the current high-level objective of each character.
 * Updated by job implementations as they progress through their loop.
 */
@Service
class CharacterContextService {
    private val objectives = ConcurrentHashMap<String, String>()

    fun setObjective(characterName: String, objective: String) {
        objectives[characterName] = objective
    }

    fun getAllObjectives(): Map<String, String> = objectives.toMap()
}
