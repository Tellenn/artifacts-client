package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.exceptions.TaskFailedException
import com.tellenn.artifacts.exceptions.UnreachableMapException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.BattleService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.EquipmentService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.AchievementService
import com.tellenn.artifacts.services.MonsterService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.TaskService
import com.tellenn.artifacts.services.CharacterContextService
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
    private val battleService: BattleService,
    private val equipmentService: EquipmentService,
    private val monsterService: MonsterService,
    private val achievementService: AchievementService,
    private val contextService: CharacterContextService,
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

            try {
                contextService.setObjective(characterName, "Obtention d'une tâche monstre")
                character = taskService.getNewMonsterTask(character)
                log.info("${character.name} is doing a new monster task")
                contextService.setObjective(characterName, "Combat : ${character.task} (${character.taskProgress}/${character.taskTotal})")
                character = taskService.doCharacterTask(character)
                character = movementService.moveToBank(character)
                character = bankService.emptyInventory(character)
                if(bankService.isInBank("tasks_coin",16)) {
                    contextService.setObjective(characterName, "Échange de récompenses de tâches (${bankService.getOne("tasks_coin").quantity} pièces)")
                    character = taskService.exchangeRewardFromBank(character)
                }
            }catch (_: TaskFailedException){
                log.info("The task is too hard for ${character.name}, he's switching out")
                handleImpossibleQuest(characterName)
            }catch (_: UnreachableMapException){
                log.info("The task is impossible to reach for ${character.name}, he's switching out")
                handleImpossibleQuest(characterName)
            }
        }while (true)

    }

    private fun handleImpossibleQuest(characterName: String) {
        character = accountClient.getCharacter(characterName).data
        if (bankService.isInBank("tasks_coin", 1)) {
            character = taskService.abandonMonsterTask(character)
        } else {
            // TODO : Quelque chose de plus intéressant
            contextService.setObjective(characterName, "Broyage de red_slime (tâche actuelle trop difficile)")
            character = equipmentService.equipBestAvailableEquipmentForMonsterInBank(character, "red_slime")
            val monsterMap = monsterService.findMonsterMap("red_slime")
            character = movementService.moveCharacterToCell(monsterMap.mapId, character)
            character = battleService.battleUntilInvIsFull(character, "red_slime")
            character = movementService.moveToBank(character)
            character = bankService.emptyInventory(character)
        }
    }
}
