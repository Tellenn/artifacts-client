package com.tellenn.artifacts.services

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the current high-level objective of each character, plus the fine-grained
 * step published by GatheringService while a craft is in progress.
 * Updated by job implementations (objective) and GatheringService (step).
 */
@Service
class CharacterContextService {
    private val objectives = ConcurrentHashMap<String, String>()
    private val steps = ConcurrentHashMap<String, String>()

    fun setObjective(characterName: String, objective: String) {
        objectives[characterName] = objective
        // Nouvel objectif = nouveau contexte : une étape de l'objectif précédent serait mensongère.
        steps.remove(characterName)
    }

    fun setStep(characterName: String, step: String) {
        steps[characterName] = step
    }

    fun clearStep(characterName: String) {
        steps.remove(characterName)
    }

    fun getAllObjectives(): Map<String, String> =
        objectives.entries.associate { (name, objective) ->
            name to (steps[name]?.let { "$objective — $it" } ?: objective)
        }
}
