package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.services.MapProximityService
import org.springframework.context.ApplicationContext

/**
 * Base class for all job implementations.
 */
open class GenericJob(
    protected val mapProximityService: MapProximityService,
    protected val applicationContext: ApplicationContext,
    protected val movementClient: MovementClient
) {
    /**
     * Initialize the job with a character.
     * @param character The character to initialize with
     * @return The initialized character
     */
    protected fun init(character: ArtifactsCharacter): ArtifactsCharacter {
        // Placeholder implementation
        return character
    }
}