package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.MainRuntime
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.config.CharacterConfig.Companion.getPredefinedCharacters
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.BattleService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.MovementService
import org.apache.logging.log4j.LogManager
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import kotlin.math.min

/**
 * Abstract base class for all job types.
 * Provides common functionality and structure for job implementations.
 */
@Component
open class GenericJob(
    val mapService: MapService,
    val movementService: MovementService,
    val bankService: BankService,
    val characterService: CharacterService,
    val accountClient: AccountClient,
    val battleService: BattleService
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

    fun catchBackCrafter(character: ArtifactsCharacter) : ArtifactsCharacter{
        val crafter = accountClient.getCharacter(getPredefinedCharacters().first { it.job == "crafter" }.name).data
        val lowestCraftLevel = min(min(crafter.weaponcraftingLevel, crafter.gearcraftingLevel), crafter.jewelrycraftingLevel) / 5 * 5
        var newCharacter = character
        if(newCharacter.level < lowestCraftLevel){
            log.info("Character ${newCharacter.name} need to catch back crafter : craft level is $lowestCraftLevel, character level is ${character.level}")
            while(newCharacter.level < lowestCraftLevel){
                newCharacter = battleService.train(newCharacter, 0)
            }
        }
        return newCharacter
    }

}
