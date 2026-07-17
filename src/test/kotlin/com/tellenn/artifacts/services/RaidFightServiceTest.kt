package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.BattleClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.CombatResponseBody
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Raid
import com.tellenn.artifacts.models.RaidInstance
import com.tellenn.artifacts.models.RaidSchedule
import com.tellenn.artifacts.utils.TimeUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant

class RaidFightServiceTest {

    private lateinit var raidService: RaidService
    private lateinit var bossFightService: BossFightService
    private lateinit var characterService: CharacterService
    private lateinit var battleClient: BattleClient
    private lateinit var combatMetrics: CombatMetrics
    private lateinit var timeUtils: TimeUtils

    @BeforeEach
    fun setUp() {
        raidService = mock(RaidService::class.java)
        bossFightService = mock(BossFightService::class.java)
        characterService = mock(CharacterService::class.java)
        battleClient = mock(BattleClient::class.java)
        combatMetrics = mock(CombatMetrics::class.java)
        timeUtils = mock(TimeUtils::class.java)
        `when`(timeUtils.now()).thenReturn(Instant.parse("2026-06-22T21:00:00Z"))
    }

    private fun raid() = Raid(
        code = "god_of_the_sun", name = "God of the Sun", description = null,
        monster = "sonnengott", schedule = RaidSchedule(listOf("monday"), 21, 0, 12), rewards = null,
    )

    private fun liveRaid(remainingHp: Long, status: String = "active"): Raid {
        val instance = RaidInstance(
            startsAt = null, endsAt = null, status = status,
            totalHp = 500000, remainingHp = remainingHp, participantCount = 1,
            endedAt = null, result = null, rewardsDistributedAt = null,
        )
        return Raid(
            code = "god_of_the_sun", name = "God of the Sun", description = null,
            monster = "sonnengott", schedule = RaidSchedule(listOf("monday"), 21, 0, 12), rewards = null,
            activeInstance = instance,
        )
    }

    private fun anyChar() = mock(ArtifactsCharacter::class.java)

    private fun namedChar(name: String): ArtifactsCharacter {
        val character = mock(ArtifactsCharacter::class.java)
        `when`(character.name).thenReturn(name)
        return character
    }

    private fun combatResult(vararg characters: ArtifactsCharacter): ArtifactsResponseBody<CombatResponseBody> {
        val body = mock(CombatResponseBody::class.java)
        `when`(body.characters).thenReturn(characters.toList())
        return ArtifactsResponseBody(body)
    }

    @Test
    fun `refreshes the party from each fight result so it keeps resting between attacks`() {
        val service = RaidFightService(raidService, bossFightService, characterService, battleClient, combatMetrics, timeUtils, 1L, 1L)

        val preparedRenoir = namedChar("Renoir")
        val preparedCloud = namedChar("Cloud")
        val preparedKepo = namedChar("Kepo")
        val foughtRenoir = namedChar("Renoir")
        val foughtCloud = namedChar("Cloud")
        val foughtKepo = namedChar("Kepo")

        `when`(raidService.getCachedRaid("god_of_the_sun")).thenReturn(raid())
        `when`(bossFightService.simulateParty(anyString(), anyString(), anyString(), anyString())).thenReturn(true)
        `when`(bossFightService.reserveParty(anyString(), anyString(), anyString())).thenReturn(true)
        `when`(bossFightService.prepareParty(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Triple(preparedRenoir, preparedCloud, preparedKepo))
        listOf(preparedRenoir, preparedCloud, preparedKepo, foughtRenoir, foughtCloud, foughtKepo)
            .forEach { `when`(characterService.rest(it)).thenReturn(it) }
        val fightResult = combatResult(foughtRenoir, foughtCloud, foughtKepo)
        `when`(battleClient.fightBoss(anyString(), anyString(), anyString())).thenReturn(fightResult)
        // wait-active alive, two alive loop passes, then dead
        `when`(raidService.getLiveRaid("god_of_the_sun"))
            .thenReturn(liveRaid(remainingHp = 500000))
            .thenReturn(liveRaid(remainingHp = 400000))
            .thenReturn(liveRaid(remainingHp = 300000))
            .thenReturn(liveRaid(remainingHp = 0))

        service.attemptRaid("god_of_the_sun")

        // The second attack must rest the character returned by the first fight,
        // not the stale prepared instance (otherwise the party never heals and dies).
        verify(characterService).rest(foughtRenoir)
        // Chaque coup porté est compté (deux passes de combat) : une métrique par attaque.
        verify(combatMetrics, times(2)).recordFight(anyString(), any())
    }

    @Test
    fun `skips with no reservation when the party cannot win`() {
        val service = RaidFightService(raidService, bossFightService, characterService, battleClient, combatMetrics, timeUtils, 1L, 0L)
        `when`(raidService.getCachedRaid("god_of_the_sun")).thenReturn(raid())
        `when`(bossFightService.simulateParty(anyString(), anyString(), anyString(), anyString())).thenReturn(false)

        service.attemptRaid("god_of_the_sun")

        verify(bossFightService, never()).reserveParty(anyString(), anyString(), anyString())
        verify(battleClient, never()).fightBoss(anyString(), anyString(), anyString())
    }

    @Test
    fun `skips fighting when the party cannot be reserved`() {
        val service = RaidFightService(raidService, bossFightService, characterService, battleClient, combatMetrics, timeUtils, 1L, 0L)
        `when`(raidService.getCachedRaid("god_of_the_sun")).thenReturn(raid())
        `when`(bossFightService.simulateParty(anyString(), anyString(), anyString(), anyString())).thenReturn(true)
        `when`(bossFightService.reserveParty(anyString(), anyString(), anyString())).thenReturn(false)

        service.attemptRaid("god_of_the_sun")

        verify(bossFightService, never()).prepareParty(anyString(), anyString(), anyString(), anyString())
        verify(battleClient, never()).fightBoss(anyString(), anyString(), anyString())
    }

    @Test
    fun `does not fight when the boss is already dead, but still releases the party`() {
        val service = RaidFightService(raidService, bossFightService, characterService, battleClient, combatMetrics, timeUtils, 1L, 0L)
        `when`(raidService.getCachedRaid("god_of_the_sun")).thenReturn(raid())
        `when`(bossFightService.simulateParty(anyString(), anyString(), anyString(), anyString())).thenReturn(true)
        `when`(bossFightService.reserveParty(anyString(), anyString(), anyString())).thenReturn(true)
        val char = anyChar()
        `when`(bossFightService.prepareParty(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Triple(char, char, char))
        // no active instance at all -> boss not available / already dead
        `when`(raidService.getLiveRaid("god_of_the_sun")).thenReturn(null)

        service.attemptRaid("god_of_the_sun")

        verify(battleClient, never()).fightBoss(anyString(), anyString(), anyString())
        verify(bossFightService).releaseParty(anyString(), anyString(), anyString())
    }

    @Test
    fun `fights while the boss is alive then stops when it dies`() {
        val service = RaidFightService(raidService, bossFightService, characterService, battleClient, combatMetrics, timeUtils, 1L, 1L)
        val char = anyChar()
        `when`(char.name).thenReturn("Renoir")
        `when`(raidService.getCachedRaid("god_of_the_sun")).thenReturn(raid())
        `when`(bossFightService.simulateParty(anyString(), anyString(), anyString(), anyString())).thenReturn(true)
        `when`(bossFightService.reserveParty(anyString(), anyString(), anyString())).thenReturn(true)
        `when`(bossFightService.prepareParty(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Triple(char, char, char))
        `when`(characterService.rest(char)).thenReturn(char)
        // wait-for-active sees it alive; first loop pass alive; second pass dead
        `when`(raidService.getLiveRaid("god_of_the_sun"))
            .thenReturn(liveRaid(remainingHp = 500000))
            .thenReturn(liveRaid(remainingHp = 250000))
            .thenReturn(liveRaid(remainingHp = 0))

        service.attemptRaid("god_of_the_sun")

        verify(battleClient, times(1)).fightBoss("Renoir", "Renoir", "Renoir")
        verify(bossFightService).releaseParty(anyString(), anyString(), anyString())
    }
}
