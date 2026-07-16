package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.exceptions.TaskFailedException
import com.tellenn.artifacts.exceptions.UnreachableMapException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.models.SimpleItem
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Couvre le cycle de tâche monstre partagé entre le fighter et les récolteurs au niveau max :
 * obtention, exécution interruptible, dépôt banque, échange de récompenses, et gestion
 * des tâches impossibles (abandon via tasks_coin ou broyage du monstre le plus
 * haut niveau gagnable).
 */
class MonsterTaskWorkerServiceTest {

    private lateinit var taskService: TaskService
    private lateinit var bankService: BankService
    private lateinit var movementService: MovementService
    private lateinit var battleService: BattleService
    private lateinit var equipmentService: EquipmentService
    private lateinit var monsterService: MonsterService
    private lateinit var accountClient: AccountClient
    private lateinit var contextService: CharacterContextService
    private lateinit var monsterTaskWorkerService: MonsterTaskWorkerService
    private lateinit var character: ArtifactsCharacter

    @BeforeEach
    fun setUp() {
        taskService = mock(TaskService::class.java)
        bankService = mock(BankService::class.java)
        movementService = mock(MovementService::class.java)
        battleService = mock(BattleService::class.java)
        equipmentService = mock(EquipmentService::class.java)
        monsterService = mock(MonsterService::class.java)
        accountClient = mock(AccountClient::class.java)
        contextService = mock(CharacterContextService::class.java)
        monsterTaskWorkerService = MonsterTaskWorkerService(
            taskService = taskService,
            bankService = bankService,
            movementService = movementService,
            battleService = battleService,
            equipmentService = equipmentService,
            monsterService = monsterService,
            accountClient = accountClient,
            contextService = contextService,
        )
        character = character()

        `when`(taskService.getNewMonsterTask(character)).thenReturn(character)
        `when`(taskService.doCharacterTask(eqCharacter(character), anyInterrupt())).thenReturn(character)
        `when`(movementService.moveToBank(character)).thenReturn(character)
        `when`(bankService.emptyInventory(character)).thenReturn(character)
        `when`(bankService.isInBank("tasks_coin", 16)).thenReturn(false)
    }

    @Test
    fun `le cycle nominal obtient une tache monstre et l'execute puis depose en banque`() {
        // when
        val result = monsterTaskWorkerService.runCycle(character)

        // then
        verify(taskService).getNewMonsterTask(character)
        verify(taskService).doCharacterTask(eqCharacter(character), anyInterrupt())
        verify(bankService).emptyInventory(character)
        verify(taskService, never()).exchangeRewardFromBank(character)
        assert(result == character)
    }

    @Test
    fun `les recompenses sont echangees quand 16 tasks_coin sont en banque`() {
        // given
        `when`(bankService.isInBank("tasks_coin", 16)).thenReturn(true)
        `when`(bankService.getOne("tasks_coin")).thenReturn(SimpleItem("tasks_coin", 16))
        `when`(taskService.exchangeRewardFromBank(character)).thenReturn(character)

        // when
        monsterTaskWorkerService.runCycle(character)

        // then
        verify(taskService).exchangeRewardFromBank(character)
    }

    @Test
    fun `une tache impossible est abandonnee quand un tasks_coin est disponible`() {
        // given
        `when`(taskService.doCharacterTask(eqCharacter(character), anyInterrupt())).thenThrow(TaskFailedException())
        `when`(accountClient.getCharacter("Kepo")).thenReturn(ArtifactsResponseBody(character))
        `when`(bankService.isInBank("tasks_coin", 1)).thenReturn(true)
        `when`(taskService.abandonMonsterTask(character)).thenReturn(character)

        // when
        monsterTaskWorkerService.runCycle(character)

        // then
        verify(taskService).abandonMonsterTask(character)
        verify(battleService, never()).battleUntilInvIsFull(eqCharacter(character), anyString())
    }

    @Test
    fun `une tache impossible sans tasks_coin declenche le broyage du monstre le plus haut niveau gagnable`() {
        // given
        givenImpossibleTaskWithoutTasksCoin()
        `when`(monsterService.findMonstersUnderLevel(20))
            .thenReturn(listOf(monster("ogre", 20), monster("wolf", 15)))
        givenMonsterOnMap("ogre", 41)
        givenMonsterOnMap("wolf", 42)
        `when`(battleService.isFightWinnable(character, "ogre")).thenReturn(false)
        `when`(battleService.isFightWinnable(character, "wolf")).thenReturn(true)
        givenGrindableMonster("wolf", 42)

        // when
        monsterTaskWorkerService.runCycle(character)

        // then
        verify(battleService).battleUntilInvIsFull(character, "wolf")
        verify(taskService, never()).abandonMonsterTask(character)
    }

    @Test
    fun `le broyage de repli ignore les boss`() {
        // given
        givenImpossibleTaskWithoutTasksCoin()
        `when`(monsterService.findMonstersUnderLevel(20))
            .thenReturn(listOf(monster("bandit_lizard_king", 20, type = "boss"), monster("wolf", 15)))
        givenMonsterOnMap("wolf", 42)
        `when`(battleService.isFightWinnable(character, "wolf")).thenReturn(true)
        givenGrindableMonster("wolf", 42)

        // when
        monsterTaskWorkerService.runCycle(character)

        // then
        verify(battleService, never()).isFightWinnable(character, "bandit_lizard_king")
        verify(battleService).battleUntilInvIsFull(character, "wolf")
    }

    @Test
    fun `le broyage de repli ignore les monstres sans map accessible`() {
        // given
        givenImpossibleTaskWithoutTasksCoin()
        `when`(monsterService.findMonstersUnderLevel(20))
            .thenReturn(listOf(monster("demon", 20), monster("wolf", 15)))
        `when`(monsterService.findMonsterMapOrNull("demon")).thenReturn(null)
        givenMonsterOnMap("wolf", 42)
        `when`(battleService.isFightWinnable(character, "wolf")).thenReturn(true)
        givenGrindableMonster("wolf", 42)

        // when
        monsterTaskWorkerService.runCycle(character)

        // then
        verify(battleService, never()).isFightWinnable(character, "demon")
        verify(battleService).battleUntilInvIsFull(character, "wolf")
    }

    @Test
    fun `le broyage de repli est abandonne quand aucun monstre n'est gagnable`() {
        // given
        givenImpossibleTaskWithoutTasksCoin()
        `when`(monsterService.findMonstersUnderLevel(20)).thenReturn(listOf(monster("wolf", 15)))
        givenMonsterOnMap("wolf", 42)
        `when`(battleService.isFightWinnable(character, "wolf")).thenReturn(false)

        // when
        val result = monsterTaskWorkerService.runCycle(character)

        // then
        verify(battleService, never()).battleUntilInvIsFull(eqCharacter(character), anyString())
        assert(result == character)
    }

    @Test
    fun `une map inaccessible est traitee comme une tache impossible`() {
        // given
        val unreachable = UnreachableMapException(mock(MapData::class.java), "Kepo")
        `when`(taskService.doCharacterTask(eqCharacter(character), anyInterrupt()))
            .thenThrow(unreachable)
        `when`(accountClient.getCharacter("Kepo")).thenReturn(ArtifactsResponseBody(character))
        `when`(bankService.isInBank("tasks_coin", 1)).thenReturn(true)
        `when`(taskService.abandonMonsterTask(character)).thenReturn(character)

        // when
        monsterTaskWorkerService.runCycle(character)

        // then
        verify(taskService).abandonMonsterTask(character)
    }

    private fun givenImpossibleTaskWithoutTasksCoin() {
        `when`(taskService.doCharacterTask(eqCharacter(character), anyInterrupt())).thenThrow(TaskFailedException())
        `when`(accountClient.getCharacter("Kepo")).thenReturn(ArtifactsResponseBody(character))
        `when`(bankService.isInBank("tasks_coin", 1)).thenReturn(false)
    }

    private fun givenMonsterOnMap(monsterCode: String, mapId: Int) {
        val map = mock(MapData::class.java)
        `when`(map.mapId).thenReturn(mapId)
        `when`(monsterService.findMonsterMapOrNull(monsterCode)).thenReturn(map)
    }

    private fun givenGrindableMonster(monsterCode: String, mapId: Int) {
        `when`(equipmentService.equipBestAvailableEquipmentForMonsterInBank(character, monsterCode))
            .thenReturn(character)
        `when`(movementService.moveCharacterToCell(mapId, character)).thenReturn(character)
        `when`(battleService.battleUntilInvIsFull(character, monsterCode)).thenReturn(character)
    }

    private fun monster(code: String, level: Int, type: String = "monster") = MonsterData(
        name = code, code = code, level = level, hp = 100,
        attackFire = 0, attackEarth = 0, attackWater = 0, attackAir = 0,
        defenseFire = 0, defenseEarth = 0, defenseWater = 0, defenseAir = 0,
        criticalStrike = 0, effects = emptyList(), minGold = 0, maxGold = 0,
        drops = null, initiative = 0, type = type,
    )

    // Mockito renvoie null depuis eq()/any(), ce que Kotlin rejette sur un paramètre
    // non-nullable : ces wrappers enregistrent le matcher puis renvoient une valeur non-nulle.
    private fun eqCharacter(value: ArtifactsCharacter): ArtifactsCharacter {
        ArgumentMatchers.eq(value)
        return value
    }

    private fun anyInterrupt(): () -> Boolean {
        ArgumentMatchers.any<() -> Boolean>()
        return { false }
    }

    private fun character() = ArtifactsCharacter(
        name = "Kepo", account = "tellenn", level = 20, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = arrayOf(), cooldown = 0, skin = null, task = "red_slime",
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0, criticalStrike = 0, speed = 0,
        haste = 0, xp = 0, maxXp = 100, taskType = "monsters", taskTotal = 5, taskProgress = 0,
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
