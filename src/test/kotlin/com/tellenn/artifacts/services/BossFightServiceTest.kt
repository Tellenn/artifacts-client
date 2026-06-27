package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BattleClient
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class BossFightServiceTest {

    private lateinit var threadService: ThreadService
    private lateinit var service: BossFightService

    @BeforeEach
    fun setUp() {
        threadService = mock(ThreadService::class.java)
        service = BossFightService(
            monsterService = mock(MonsterService::class.java),
            battleSimulatorService = mock(BattleSimulatorService::class.java),
            accountClient = mock(AccountClient::class.java),
            equipmentService = mock(EquipmentService::class.java),
            movementService = mock(MovementService::class.java),
            mapService = mock(MapService::class.java),
            threadService = threadService,
            battleClient = mock(BattleClient::class.java),
            characterService = mock(CharacterService::class.java),
            bankService = mock(BankService::class.java),
            merchantService = mock(MerchantService::class.java),
        )
    }

    @Test
    fun `reserveParty succeeds when all three are free`() {
        `when`(threadService.reserveCharacter("Renoir")).thenReturn(true)
        `when`(threadService.reserveAndInterruptCharacter("Cloud")).thenReturn(true)
        `when`(threadService.reserveAndInterruptCharacter("Kepo")).thenReturn(true)

        assertTrue(service.reserveParty("Renoir", "Cloud", "Kepo"))
    }

    @Test
    fun `reserveParty rolls back the master when the second character is busy`() {
        `when`(threadService.reserveCharacter("Renoir")).thenReturn(true)
        `when`(threadService.reserveAndInterruptCharacter("Cloud")).thenReturn(false)

        assertFalse(service.reserveParty("Renoir", "Cloud", "Kepo"))

        verify(threadService).releaseCharacter("Renoir")
        verify(threadService, never()).reserveAndInterruptCharacter("Kepo")
    }

    @Test
    fun `reserveParty rolls back master and second when the third is busy`() {
        `when`(threadService.reserveCharacter("Renoir")).thenReturn(true)
        `when`(threadService.reserveAndInterruptCharacter("Cloud")).thenReturn(true)
        `when`(threadService.reserveAndInterruptCharacter("Kepo")).thenReturn(false)

        assertFalse(service.reserveParty("Renoir", "Cloud", "Kepo"))

        verify(threadService).releaseCharacter("Renoir")
        verify(threadService).restartCharacterThread("Cloud")
    }

    @Test
    fun `releaseParty releases master and restarts the two slaves`() {
        service.releaseParty("Renoir", "Cloud", "Kepo")

        verify(threadService).releaseCharacter("Renoir")
        verify(threadService).restartCharacterThread("Cloud")
        verify(threadService).restartCharacterThread("Kepo")
    }
}
