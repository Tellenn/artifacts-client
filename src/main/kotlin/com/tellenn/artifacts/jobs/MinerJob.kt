package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.services.MapProximityService
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * Job implementation for characters with the "miner" job.
 */
@Component
class MinerJob(
    mapProximityService: MapProximityService,
    applicationContext: ApplicationContext,
    movementClient: MovementClient
) : GenericJob(mapProximityService, applicationContext, movementClient) {

    lateinit var character: ArtifactsCharacter

    fun run(initCharacter: ArtifactsCharacter) {
        character = init(initCharacter)
    }
}
