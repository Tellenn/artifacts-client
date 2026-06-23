package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.BattleClient
import com.tellenn.artifacts.exceptions.MapContentNotFoundException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.utils.TimeUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Decides whether the default party can beat a raid monster and, if so, fights the
 * raid instance until the boss dies (its own kills, or another player's).
 */
@Service
class RaidFightService(
    private val raidService: RaidService,
    private val bossFightService: BossFightService,
    private val characterService: CharacterService,
    private val battleClient: BattleClient,
    private val timeUtils: TimeUtils,
    @param:Value("\${raids.poll-interval-ms:30000}") private val pollIntervalMs: Long,
    @param:Value("\${raids.max-wait-minutes:10}") private val maxWaitMinutes: Long,
) {
    private val logger = LoggerFactory.getLogger(RaidFightService::class.java)

    private val character1 = BossFightService.DEFAULT_CHARACTER_1
    private val character2 = BossFightService.DEFAULT_CHARACTER_2
    private val character3 = BossFightService.DEFAULT_CHARACTER_3

    /**
     * Attempts the raid identified by [raidCode]: simulate, and if winnable, reserve
     * the party, gear up, travel, and fight until the raid instance is finished.
     */
    fun attemptRaid(raidCode: String) {
        val raid = raidService.getCachedRaid(raidCode)
        if (raid == null) {
            logger.warn("Raid {} not found in cache, skipping", raidCode)
            return
        }
        val monsterCode = raid.monster

        if (!bossFightService.simulateParty(character1, character2, character3, monsterCode)) {
            logger.warn("Raid {} skipped: party is not strong enough to beat {}", raidCode, monsterCode)
            return
        }

        if (!bossFightService.reserveParty(character1, character2, character3)) {
            logger.warn("Raid {} skipped: party is not available", raidCode)
            return
        }

        try {
            val (char1, char2, char3) = bossFightService.prepareParty(character1, character2, character3, monsterCode)
            runRaidLoop(raidCode, char1, char2, char3)
        } catch (_: MapContentNotFoundException) {
            logger.info("Raid monster {} is no longer available", monsterCode)
        } catch (e: Exception) {
            logger.error("Uncaught error while running raid {}", raidCode, e)
        } finally {
            bossFightService.releaseParty(character1, character2, character3)
        }
    }

    private fun runRaidLoop(
        raidCode: String,
        char1: ArtifactsCharacter,
        char2: ArtifactsCharacter,
        char3: ArtifactsCharacter,
    ) {
        if (!waitForActiveInstance(raidCode)) {
            logger.warn("Raid {} never became active, aborting", raidCode)
            return
        }
        var c1 = char1
        var c2 = char2
        var c3 = char3
        while (true) {
            val instance = raidService.getLiveRaid(raidCode)?.activeInstance
            if (instance == null || instance.remainingHp <= 0 || instance.isFinished()) {
                logger.info("Raid {} finished (boss dead or instance over)", raidCode)
                break
            }
            c1 = characterService.rest(c1)
            c2 = characterService.rest(c2)
            c3 = characterService.rest(c3)
            // Refresh the party from the fight result: the next rest() must see the
            // post-fight HP, otherwise it reads stale full HP, skips resting, and the
            // party fights on until it dies.
            battleClient.fightBoss(c1.name, c2.name, c3.name).data.characters.forEach {
                when (it.name) {
                    c1.name -> c1 = it
                    c2.name -> c2 = it
                    c3.name -> c3 = it
                }
            }
        }
    }

    /**
     * Bounded poll waiting for the raid instance to go active (it activates at the
     * scheduled start, shortly after this party finishes gearing up). Returns true
     * once an active instance with HP remaining is observed.
     *
     * The Thread.sleep here is a scheduling wait for external raid state, not a
     * per-action sleep — it is the sole sanctioned exception to the no-sleep rule.
     */
    private fun waitForActiveInstance(raidCode: String): Boolean {
        val deadline = timeUtils.now().plus(Duration.ofMinutes(maxWaitMinutes))
        while (timeUtils.now().isBefore(deadline)) {
            val instance = raidService.getLiveRaid(raidCode)?.activeInstance
            if (instance != null && instance.remainingHp > 0 && !instance.isFinished()) {
                return true
            }
            Thread.sleep(pollIntervalMs)
        }
        return false
    }
}
