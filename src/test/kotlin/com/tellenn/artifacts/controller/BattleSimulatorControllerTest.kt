package com.tellenn.artifacts.controller

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.EquipmentService
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import com.tellenn.artifacts.services.battlesim.TestCharacters
import com.tellenn.artifacts.services.battlesim.model.FightOutcome
import com.tellenn.artifacts.services.battlesim.model.LocalSimulationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

/**
 * Unit test for BattleSimulatorController, following the project's pragmatic controller-test pattern:
 * mock the collaborators, call the endpoint method directly, assert on the returned value
 * (no HTTP layer nor TestContainers).
 */
class BattleSimulatorControllerTest {

    private lateinit var battleSimulatorService: BattleSimulatorService
    private lateinit var accountClient: AccountClient
    private lateinit var equipmentService: EquipmentService
    private lateinit var controller: BattleSimulatorController

    private val outcomeWithLogs = FightOutcome(
        charactersWin = true,
        turns = 4,
        finalHp = mapOf("hero" to 42, "slime" to 0),
        logs = listOf("hero hits slime for 10", "slime dies"),
    )

    private val localResult = LocalSimulationResult(
        wins = 7, losses = 3, winrate = 70, avgTurns = 4.2, results = listOf(outcomeWithLogs),
    )

    @BeforeEach
    fun setUp() {
        battleSimulatorService = mock(BattleSimulatorService::class.java)
        accountClient = mock(AccountClient::class.java)
        equipmentService = mock(EquipmentService::class.java)
        controller = BattleSimulatorController(battleSimulatorService, accountClient, equipmentService)

        `when`(accountClient.getCharacter(anyString()))
            .thenReturn(ArtifactsResponseBody(TestCharacters.blank("hero")))
        `when`(
            battleSimulatorService.simulateLocally(
                anyString(), anyObject<ArtifactsCharacter>(), anyInt(), anyObject<Long?>(),
            ),
        ).thenReturn(localResult)
    }

    @Test
    fun `should strip combat logs by default to keep the response light`() {
        // when
        val result = controller.simulateLocally(
            monsterCode = "slime", characterName = "hero",
            runs = 10, seed = 1L, useBankItems = false, includeLogs = false,
        )

        // then
        assertEquals(70, result.winrate)
        assertTrue(result.results.all { it.logs.isEmpty() })
    }

    @Test
    fun `should keep combat logs when includeLogs is requested`() {
        // when
        val result = controller.simulateLocally(
            monsterCode = "slime", characterName = "hero",
            runs = 10, seed = 1L, useBankItems = false, includeLogs = true,
        )

        // then
        assertEquals(listOf("hero hits slime for 10", "slime dies"), result.results.single().logs)
    }
}