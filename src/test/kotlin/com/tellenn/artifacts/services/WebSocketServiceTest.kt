package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class WebSocketServiceTest {

    private lateinit var merchantService: MerchantService
    private lateinit var accountClient: AccountClient
    private lateinit var bankService: BankService
    private lateinit var movementService: MovementService
    private lateinit var gatheringService: GatheringService
    private lateinit var threadService: ThreadService
    private lateinit var battleService: BattleService
    private lateinit var battleSimulatorService: BattleSimulatorService
    private lateinit var monsterService: MonsterService
    private lateinit var characterService: CharacterService
    private lateinit var mapService: MapService
    private lateinit var webSocketService: WebSocketService

    @BeforeEach
    fun setUp() {
        merchantService = mock(MerchantService::class.java)
        accountClient = mock(AccountClient::class.java)
        bankService = mock(BankService::class.java)
        movementService = mock(MovementService::class.java)
        gatheringService = mock(GatheringService::class.java)
        threadService = mock(ThreadService::class.java)
        battleService = mock(BattleService::class.java)
        battleSimulatorService = mock(BattleSimulatorService::class.java)
        monsterService = mock(MonsterService::class.java)
        characterService = mock(CharacterService::class.java)
        mapService = mock(MapService::class.java)
        webSocketService = WebSocketService(
            merchantService, accountClient, bankService, movementService,
            gatheringService, threadService, battleService, battleSimulatorService,
            monsterService, characterService, mapService
        )
    }

    @Test
    fun `fightEventMonster stops after three consecutive failures instead of looping forever`() {
        val character = stubFightRound()
        // Every round ends without a full inventory -> the fight could not be sustained
        `when`(characterService.isInventoryFull(character)).thenReturn(false)

        webSocketService.fightEventMonster("Cloud", 91, "corrupted_ogre")

        verify(battleService, times(3)).battleUntilInvIsFull(character, "corrupted_ogre")
    }

    @Test
    fun `fightEventMonster keeps fighting while it can clear the monster and only stops on a failure streak`() {
        val character = stubFightRound()
        // Two cleared rounds reset the counter, then a streak of three failures stops it
        `when`(characterService.isInventoryFull(character))
            .thenReturn(true, true, false, false, false)

        webSocketService.fightEventMonster("Cloud", 91, "corrupted_ogre")

        verify(battleService, times(5)).battleUntilInvIsFull(character, "corrupted_ogre")
    }

    @Test
    fun `fightEventMonster stops without fighting once the target monster left the cell`() {
        val character = stubFightRound()
        // Event ended: the cell no longer holds our target (gone, or replaced by another mob)
        `when`(mapService.isMonsterPresentAt(91, "corrupted_ogre")).thenReturn(false)

        webSocketService.fightEventMonster("Cloud", 91, "corrupted_ogre")

        verify(battleService, never()).battleUntilInvIsFull(character, "corrupted_ogre")
    }

    private fun stubFightRound(): ArtifactsCharacter {
        val character = mock(ArtifactsCharacter::class.java)
        `when`(accountClient.getCharacter("Cloud")).thenReturn(ArtifactsResponseBody(character))
        `when`(movementService.moveToBank(character)).thenReturn(character)
        `when`(bankService.emptyInventory(character)).thenReturn(character)
        `when`(movementService.moveCharacterToCell(91, character)).thenReturn(character)
        `when`(battleService.battleUntilInvIsFull(character, "corrupted_ogre")).thenReturn(character)
        // By default the target monster is still present; the "event ended" test overrides this.
        `when`(mapService.isMonsterPresentAt(91, "corrupted_ogre")).thenReturn(true)
        return character
    }
}
