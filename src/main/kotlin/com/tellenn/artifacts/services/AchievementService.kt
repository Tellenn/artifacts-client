package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.db.repositories.ResourceRepository
import com.tellenn.artifacts.models.Achievement
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Objective
import org.apache.logging.log4j.LogManager
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.stereotype.Service
import kotlin.math.min

@Service
class AchievementService(
    private val accountClient: AccountClient,
    private val resourceRepository: ResourceRepository,
    private val gatheringService: GatheringService,
    private val battleService: BattleService,
    private val movementService: MovementService,
    private val bankService: BankService,
    private val equipmentService: EquipmentService,
    private val mapService: MapService,
) {

    companion object {
        private val log = LogManager.getLogger(AchievementService::class.java)
    }

    fun getUncompletedAchievements(): List<Achievement> {
        val achievements = mutableListOf<Achievement>()
        var page = 1
        do {
            val response = accountClient.getAccountAchievements(completed = false, page = page)
            achievements.addAll(response.data)
            page++
        } while (page <= response.pages)
        return achievements
    }

    fun findObjectiveForCharacter(characterJob: String): Objective? {
        return getUncompletedAchievements()
            .flatMap { it.objectives }
            .filter { it.progress != null && it.progress < it.total }
            .filter { matchesCharacterJob(it, characterJob) }
            .minByOrNull { it.total - (it.progress ?: 0) }
    }

    fun executeAchievement(character: ArtifactsCharacter, characterJob: String): ArtifactsCharacter {
        val objective = findObjectiveForCharacter(characterJob)
        if (objective == null) {
            log.info("No achievement objective found for job $characterJob")
            return character
        }

        val target = objective.target ?: return character
        val remaining = objective.total - (objective.progress ?: 0)
        log.info("${character.name} working on achievement: ${objective.type} $target ($remaining remaining)")

        var currentCharacter = movementService.moveToBank(character)
        currentCharacter = bankService.emptyInventory(currentCharacter)

        when (objective.type) {
            "gathering", "crafting", "cooking" -> {
                val quantity = min(remaining, currentCharacter.inventoryMaxItems - 10)
                val allowFight = objective.type == "crafting"
                currentCharacter = gatheringService.craftOrGather(currentCharacter, target, quantity, allowFight = allowFight)
            }
            "combat_kill" -> {
                currentCharacter = equipmentService.equipBestAvailableEquipmentForMonsterInBank(currentCharacter, target)
                val map = mapService.findClosestMap(currentCharacter, contentCode = target)
                currentCharacter = movementService.moveCharacterToCell(map.mapId, currentCharacter)
                currentCharacter = battleService.battleUntilInvIsFull(currentCharacter, target)
            }
            else -> log.warn("${character.name}: unhandled achievement objective type '${objective.type}' for target '$target' — skipping")
        }

        currentCharacter = movementService.moveToBank(currentCharacter)
        currentCharacter = bankService.emptyInventory(currentCharacter)
        return currentCharacter
    }

    private fun matchesCharacterJob(objective: Objective, characterJob: String): Boolean {
        return when (objective.type) {
            "combat_kill" -> characterJob == "fighter"
            "crafting" -> characterJob == "crafter"
            "cooking" -> characterJob == "alchemist"
            "gathering" -> {
                val skill = resolveGatheringSkill(objective.target ?: return false)
                when (skill) {
                    "mining" -> characterJob == "miner"
                    "woodcutting" -> characterJob == "woodworker"
                    "fishing" -> characterJob == "alchemist"
                    else -> false
                }
            }
            else -> false
        }
    }

    private fun resolveGatheringSkill(target: String): String? {
        val resources = resourceRepository.findByDropsCode(target)
        if (resources.isNotEmpty()) return resources.first().skill
        return try {
            resourceRepository.findByCode(target).skill
        } catch (_: EmptyResultDataAccessException) {
            log.warn("Could not resolve gathering skill for target: $target")
            null
        }
    }
}
