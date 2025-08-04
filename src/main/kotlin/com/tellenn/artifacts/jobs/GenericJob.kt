package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.MainRuntime
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.MovementService
import org.apache.logging.log4j.LogManager
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * Abstract base class for all job types.
 * Provides common functionality and structure for job implementations.
 */
@Component
open class GenericJob(
    val mapService: MapService,
    val applicationContext: ApplicationContext,
    val movementService: MovementService,
    val bankService: BankService,
    val characterService: CharacterService,
    val accountClient: AccountClient
) {
    val log = LogManager.getLogger(MainRuntime::class.java)

    /**
     * Initialization for clean re-run, return ever character to the closest bank for an inventory cleanup
     * This is the main entry point for job execution.
     */
    fun init(characterName : String) : ArtifactsCharacter{
        var tempCharacter = accountClient.getCharacter(characterName).data
        tempCharacter = bankService.moveToBank(tempCharacter)
        tempCharacter = bankService.emptyInventory(tempCharacter)
        tempCharacter = characterService.rest(tempCharacter)
        return tempCharacter
    }

}
