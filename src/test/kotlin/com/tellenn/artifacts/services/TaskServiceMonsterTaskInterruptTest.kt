package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.clients.TaskClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.RewardDataResponseBody
import com.tellenn.artifacts.clients.responses.SimulationResult
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`


/**
 * Couvre la suspension d'une monster task via `interruptWhen` : le fighter sort de la
 * boucle de combat sans compléter ni abandonner la task (la progression est conservée
 * côté serveur), pour aller produire les tâches "mob" du pool de récolte.
 */
class TaskServiceMonsterTaskInterruptTest {

    private lateinit var bankService: BankService
    private lateinit var movementService: MovementService
    private lateinit var taskClient: TaskClient
    private lateinit var battleService: BattleService
    private lateinit var characterService: CharacterService
    private lateinit var equipmentService: EquipmentService
    private lateinit var mapService: MapService
    private lateinit var battleSimulatorService: BattleSimulatorService
    private lateinit var taskService: TaskService
    private lateinit var character: ArtifactsCharacter

    @BeforeEach
    fun setUp() {
        bankService = mock(BankService::class.java)
        movementService = mock(MovementService::class.java)
        taskClient = mock(TaskClient::class.java)
        battleService = mock(BattleService::class.java)
        characterService = mock(CharacterService::class.java)
        equipmentService = mock(EquipmentService::class.java)
        mapService = mock(MapService::class.java)
        battleSimulatorService = mock(BattleSimulatorService::class.java)
        taskService = TaskService(
            bankService = bankService,
            movementService = movementService,
            taskClient = taskClient,
            gatheringService = mock(GatheringService::class.java),
            itemService = mock(ItemService::class.java),
            battleService = battleService,
            characterService = characterService,
            equipmentService = equipmentService,
            mapService = mapService,
            accountClient = mock(AccountClient::class.java),
            characterClient = mock(CharacterClient::class.java),
            battleSimulatorService = battleSimulatorService,
        )
        character = character(taskTotal = 5, taskProgress = 0)

        val monsterMap = mock(MapData::class.java)
        `when`(monsterMap.mapId).thenReturn(42)
        `when`(mapService.findClosestMap(character, contentCode = "red_slime"))
            .thenReturn(monsterMap)
        `when`(equipmentService.findBestEquipmentForMonsterInBank(character, "red_slime"))
            .thenReturn(mutableMapOf<String, ItemDetails?>())
        `when`(battleSimulatorService.simulateWithApi("red_slime", character))
            .thenReturn(ArtifactsResponseBody(SimulationResult(wins = 10, losses = 0, winrate = 100, results = emptyList())))
        `when`(equipmentService.equipBestAvailableEquipmentForMonsterInBank(character, "red_slime"))
            .thenReturn(character)
        `when`(movementService.moveCharacterToCell(42, character)).thenReturn(character)
        `when`(movementService.moveCharacterToMaster("monsters", character)).thenReturn(character)
        `when`(battleService.battle(character, "red_slime")).thenReturn(character)
        `when`(characterService.isInventoryFull(character)).thenReturn(false)

        val reward = mock(RewardDataResponseBody::class.java)
        `when`(reward.character).thenReturn(character)
        `when`(taskClient.completeTask("Cloud")).thenReturn(ArtifactsResponseBody(reward))
    }

    @Test
    fun `interruptWhen vrai suspend la task avant le premier combat sans la completer`() {
        // when
        taskService.completeMonsterTask(character) { true }

        // then : aucun combat, la task n'est ni complétée ni abandonnée
        verify(battleService, never()).battle(character, "red_slime")
        verify(taskClient, never()).completeTask("Cloud")
        verify(taskClient, never()).abandonTask("Cloud")
    }

    @Test
    fun `l'interruption en cours de task conserve la progression deja faite`() {
        // given : interruption demandée après 2 kills
        var kills = 0
        `when`(battleService.battle(character, "red_slime")).thenAnswer {
            kills++
            character
        }

        // when
        taskService.completeMonsterTask(character) { kills >= 2 }

        // then : 2 combats sur les 5 requis, task laissée en cours
        verify(battleService, times(2)).battle(character, "red_slime")
        verify(taskClient, never()).completeTask("Cloud")
    }

    @Test
    fun `par defaut la task va a son terme et est completee`() {
        // given
        val oneKill = character(taskTotal = 1, taskProgress = 0)
        val oneKillMap = mock(MapData::class.java)
        `when`(oneKillMap.mapId).thenReturn(42)
        `when`(mapService.findClosestMap(oneKill, contentCode = "red_slime"))
            .thenReturn(oneKillMap)
        `when`(equipmentService.findBestEquipmentForMonsterInBank(oneKill, "red_slime"))
            .thenReturn(mutableMapOf<String, ItemDetails?>())
        `when`(battleSimulatorService.simulateWithApi("red_slime", oneKill))
            .thenReturn(ArtifactsResponseBody(SimulationResult(wins = 10, losses = 0, winrate = 100, results = emptyList())))
        `when`(equipmentService.equipBestAvailableEquipmentForMonsterInBank(oneKill, "red_slime"))
            .thenReturn(oneKill)
        `when`(movementService.moveCharacterToCell(42, oneKill)).thenReturn(oneKill)
        `when`(movementService.moveCharacterToMaster("monsters", oneKill)).thenReturn(oneKill)
        `when`(battleService.battle(oneKill, "red_slime")).thenReturn(oneKill)
        `when`(characterService.isInventoryFull(oneKill)).thenReturn(false)
        val reward = mock(RewardDataResponseBody::class.java)
        `when`(reward.character).thenReturn(oneKill)
        `when`(taskClient.completeTask("Cloud")).thenReturn(ArtifactsResponseBody(reward))

        // when
        taskService.completeMonsterTask(oneKill)

        // then
        verify(battleService, times(1)).battle(oneKill, "red_slime")
        verify(taskClient).completeTask("Cloud")
    }

    @Test
    fun `doCharacterTask propage interruptWhen a la monster task`() {
        // when
        taskService.doCharacterTask(character) { true }

        // then
        verify(battleService, never()).battle(character, "red_slime")
        verify(taskClient, never()).completeTask("Cloud")
    }

    private fun character(taskTotal: Int, taskProgress: Int) = ArtifactsCharacter(
        name = "Cloud", account = "tellenn", level = 20, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = arrayOf(), cooldown = 0, skin = null, task = "red_slime",
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0, criticalStrike = 0, speed = 0,
        haste = 0, xp = 0, maxXp = 100, taskType = "monsters", taskTotal = taskTotal, taskProgress = taskProgress,
        miningXp = 0, miningMaxXp = 100, miningLevel = 1, woodcuttingXp = 0, woodcuttingMaxXp = 100,
        woodcuttingLevel = 1, fishingXp = 0, fishingMaxXp = 100, fishingLevel = 1,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 100, weaponcraftingLevel = 1,
        gearcraftingXp = 0, gearcraftingMaxXp = 100, gearcraftingLevel = 1,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 100, jewelrycraftingLevel = 1,
        cookingXp = 0, cookingMaxXp = 100, cookingLevel = 1, alchemyXp = 0, alchemyMaxXp = 100, alchemyLevel = 1,
        inventoryMaxItems = 100, attackFire = 0, attackEarth = 0, attackWater = 0, attackAir = 0,
        dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0, resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
        weaponSlot = null, runeSlot = null, shieldSlot = null, helmetSlot = null, bodyArmorSlot = null,
        legArmorSlot = null, bootsSlot = null, ring1Slot = null, ring2Slot = null, amuletSlot = null,
        artifact1Slot = null, artifact2Slot = null, artifact3Slot = null,
        utility1Slot = "", utility1SlotQuantity = 0, utility2Slot = "", utility2SlotQuantity = 0,
        bagSlot = null, cooldownExpiration = Instant.now(),
    )
}
