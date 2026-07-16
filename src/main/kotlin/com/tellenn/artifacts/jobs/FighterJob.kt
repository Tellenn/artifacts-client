package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.AchievementService
import com.tellenn.artifacts.services.MonsterTaskWorkerService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.TaskService
import com.tellenn.artifacts.services.CharacterContextService
import com.tellenn.artifacts.services.GatheringWorkerService
import org.springframework.stereotype.Component

/**
 * Job implementation for characters with the "fighter" job.
 */
@Component
class FighterJob(
    mapService: MapService,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    accountClient: AccountClient,
    taskService: TaskService,
    private val achievementService: AchievementService,
    private val contextService: CharacterContextService,
    private val gatheringWorkerService: GatheringWorkerService,
    private val monsterTaskWorkerService: MonsterTaskWorkerService,
) : GenericJob(mapService, movementService, bankService, characterService, accountClient, taskService) {

    lateinit var character: ArtifactsCharacter

    fun run(characterName: String) {
        character = init(characterName)
        do{
            if (isCrafterMaxLevel()) {
                contextService.setObjective(characterName, "Exécution des achievements (crafter max)")
                character = achievementService.executeAchievement(character, "fighter")
                continue
            }

            if (gatheringWorkerService.hasOpenTasks(MOB_SKILLS, mobLevels())) {
                contextService.setObjective(characterName, "Production pour le pool du crafter")
                val poolResult = gatheringWorkerService.workOpenTasks(character, MOB_SKILLS, mobLevels(), allowFight = true)
                character = poolResult.character
                if (poolResult.produced > 0) continue
            }

            character = monsterTaskWorkerService.runCycle(character) {
                gatheringWorkerService.hasOpenTasks(MOB_SKILLS, mobLevels())
            }
        }while (true)

    }

    private fun mobLevels() = mapOf("mob" to character.level)

    companion object {
        private val MOB_SKILLS = listOf("mob")
    }
}
