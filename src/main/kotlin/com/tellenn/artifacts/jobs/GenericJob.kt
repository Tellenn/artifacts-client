package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.MainRuntime
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.services.MapProximityService
import org.apache.logging.log4j.LogManager
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * Abstract base class for all job types.
 * Provides common functionality and structure for job implementations.
 */
@Component
open class GenericJob(
    val mapProximityService: MapProximityService,
    val applicationContext: ApplicationContext,
    val movementClient: MovementClient
) {
    val log = LogManager.getLogger(MainRuntime::class.java)

    /**
     * Initialization for clean re-run, return ever character to the closest bank for an inventory cleanup
     * This is the main entry point for job execution.
     */
    fun init(character: ArtifactsCharacter) : ArtifactsCharacter{
        log.info("Character details - Name: ${character.name}, Level: ${character.level}")
        var tempCharacter : ArtifactsCharacter
        val closestBank = mapProximityService.findClosestMap(character = character, contentCode = "bank")
        tempCharacter = movementClient.moveCharacterToCell(character.name, closestBank.x, closestBank.y).data.character
        return tempCharacter
    }

}
