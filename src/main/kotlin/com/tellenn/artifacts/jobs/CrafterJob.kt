package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.MovementService
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * Job implementation for characters with the "crafter" job.
 */
@Component
class CrafterJob(
    mapService: MapService,
    applicationContext: ApplicationContext,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService
) : GenericJob(mapService, applicationContext, movementService, bankService, characterService) {

    lateinit var character: ArtifactsCharacter

    fun run(initCharacter: ArtifactsCharacter) {
        character = init(initCharacter)
    }
}
