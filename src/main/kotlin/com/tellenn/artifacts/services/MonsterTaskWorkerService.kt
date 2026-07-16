package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.exceptions.TaskFailedException
import com.tellenn.artifacts.exceptions.UnreachableMapException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.models.MonsterData
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

/**
 * Cycle de tâche monstre partagé entre le fighter et les récolteurs dont la compétence
 * est au niveau max : obtention d'une tâche, exécution interruptible, dépôt en banque,
 * échange des récompenses, et repli quand la tâche est impossible (abandon si un
 * tasks_coin est disponible, sinon broyage du monstre le plus haut niveau gagnable).
 */
@Service
class MonsterTaskWorkerService(
    private val taskService: TaskService,
    private val bankService: BankService,
    private val movementService: MovementService,
    private val battleService: BattleService,
    private val equipmentService: EquipmentService,
    private val monsterService: MonsterService,
    private val accountClient: AccountClient,
    private val contextService: CharacterContextService,
) {

    fun runCycle(character: ArtifactsCharacter, interruptWhen: () -> Boolean = { false }): ArtifactsCharacter {
        var newCharacter = character
        try {
            contextService.setObjective(newCharacter.name, "Obtention d'une tâche monstre")
            newCharacter = taskService.getNewMonsterTask(newCharacter)
            log.info("${newCharacter.name} is doing a new monster task")
            contextService.setObjective(newCharacter.name, "Combat : ${newCharacter.task} (${newCharacter.taskProgress}/${newCharacter.taskTotal})")
            newCharacter = taskService.doCharacterTask(newCharacter, interruptWhen)
            newCharacter = movementService.moveToBank(newCharacter)
            newCharacter = bankService.emptyInventory(newCharacter)
            if (bankService.isInBank("tasks_coin", REWARD_EXCHANGE_THRESHOLD)) {
                contextService.setObjective(newCharacter.name, "Échange de récompenses de tâches (${bankService.getOne("tasks_coin").quantity} pièces)")
                newCharacter = taskService.exchangeRewardFromBank(newCharacter)
            }
        } catch (_: TaskFailedException) {
            log.info("The task is too hard for ${newCharacter.name}, he's switching out")
            newCharacter = handleImpossibleQuest(newCharacter.name)
        } catch (_: UnreachableMapException) {
            log.info("The task is impossible to reach for ${newCharacter.name}, he's switching out")
            newCharacter = handleImpossibleQuest(newCharacter.name)
        }
        return newCharacter
    }

    private fun handleImpossibleQuest(characterName: String): ArtifactsCharacter {
        var newCharacter = accountClient.getCharacter(characterName).data
        if (bankService.isInBank("tasks_coin", 1)) {
            newCharacter = taskService.abandonMonsterTask(newCharacter)
        } else {
            val (monster, monsterMap) = findStrongestKillableMonster(newCharacter) ?: run {
                log.warn("No winnable fallback monster found for $characterName, skipping the grind")
                return newCharacter
            }
            contextService.setObjective(characterName, "Broyage de ${monster.code} (tâche actuelle trop difficile)")
            newCharacter = equipmentService.equipBestAvailableEquipmentForMonsterInBank(newCharacter, monster.code)
            newCharacter = movementService.moveCharacterToCell(monsterMap.mapId, newCharacter)
            newCharacter = battleService.battleUntilInvIsFull(newCharacter, monster.code)
            newCharacter = movementService.moveToBank(newCharacter)
            newCharacter = bankService.emptyInventory(newCharacter)
        }
        return newCharacter
    }

    /**
     * Monstre le plus haut niveau que [character] peut vaincre (simulation via
     * [BattleService.isFightWinnable]), hors boss et monstres sans map accessible
     * (événements inactifs), avec la map où le combattre.
     */
    private fun findStrongestKillableMonster(character: ArtifactsCharacter): Pair<MonsterData, MapData>? =
        monsterService.findMonstersUnderLevel(character.level)
            .filter { it.type != "boss" }
            .firstNotNullOfOrNull { monster ->
                monsterService.findMonsterMapOrNull(monster.code)
                    ?.takeIf { battleService.isFightWinnable(character, monster.code) }
                    ?.let { monster to it }
            }

    companion object {
        private const val REWARD_EXCHANGE_THRESHOLD = 16
        private val log = LogManager.getLogger(MonsterTaskWorkerService::class.java)
    }
}
