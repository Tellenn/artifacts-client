package com.tellenn.artifacts.services

import com.tellenn.artifacts.db.documents.GatheringTaskDocument
import com.tellenn.artifacts.exceptions.BattleLostException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.any
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Helpers null-safe pour les matchers Mockito sur des paramètres Kotlin non-nullables
 * (même pattern que GatheringServiceLevelingPoolTest).
 */
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

private fun <T> eqObject(value: T): T {
    eq(value)
    return uninitialized()
}

class GatheringWorkerServiceTest {

    private lateinit var gatheringTaskService: GatheringTaskService
    private lateinit var gatheringService: GatheringService
    private lateinit var movementService: MovementService
    private lateinit var bankService: BankService
    private lateinit var itemService: ItemService
    private lateinit var service: GatheringWorkerService
    private lateinit var character: ArtifactsCharacter

    @BeforeEach
    fun setUp() {
        gatheringTaskService = mock(GatheringTaskService::class.java)
        gatheringService = mock(GatheringService::class.java)
        movementService = mock(MovementService::class.java)
        bankService = mock(BankService::class.java)
        itemService = mock(ItemService::class.java)
        character = character(inventoryMaxItems = 100)
        service = GatheringWorkerService(
            gatheringTaskService, gatheringService, movementService, bankService, itemService
        )

        `when`(movementService.moveToBank(anyObject(), anyBoolean())).thenReturn(character)
        `when`(bankService.emptyInventory(anyObject())).thenReturn(character)
        `when`(
            gatheringService.craftOrGather(
                anyObject(), anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean()
            )
        ).thenAnswer { it.getArgument(0) }
    }

    private fun task(code: String, skill: String): GatheringTaskDocument =
        GatheringTaskDocument(materialCode = code, skill = skill, targetQuantity = 50, remaining = 50)

    private fun item(code: String): ItemDetails =
        ItemDetails(
            code = code, name = code, description = "", type = "resource", subtype = "mining",
            level = 5, tradeable = true, craft = null, effects = emptyList(), conditions = emptyList()
        )

    private fun stubMaterial(code: String, invSize: Int) {
        val details = item(code)
        `when`(itemService.getItem(code)).thenReturn(details)
        `when`(itemService.getInvSizeToCraft(eqObject(details))).thenReturn(invSize)
    }

    @Test
    fun `sans tache ouverte rien n'est produit et le personnage est rendu tel quel`() {
        // given
        `when`(gatheringTaskService.openTasksFor(listOf("mining"), mapOf("mining" to 20)))
            .thenReturn(emptyList())

        // when
        val result = service.workOpenTasks(character, listOf("mining"), mapOf("mining" to 20))

        // then
        assertEquals(character, result.character)
        assertEquals(0, result.produced)
        verify(gatheringTaskService, never()).produceOpenSlices(anyString(), anyString(), anyInt(), anyObject())
    }

    @Test
    fun `reserve des tranches bornees par l'inventaire au nom du personnage`() {
        // given : footprint 2 → chunk = levelingGatherChunkSize(2, 50, 100) = 100 / 2 = 50
        `when`(gatheringTaskService.openTasksFor(listOf("mining"), mapOf("mining" to 20)))
            .thenReturn(listOf(task("iron_ore", "mining")))
        stubMaterial("iron_ore", invSize = 2)

        // when
        service.workOpenTasks(character, listOf("mining"), mapOf("mining" to 20))

        // then
        verify(gatheringTaskService).produceOpenSlices(eqObject("iron_ore"), eqObject("Kepo"), eq(50), anyObject())
    }

    @Test
    fun `un materiau brut (footprint 0) demande la quantite restante en une seule tranche`() {
        // given : red_slimeball est un mob drop, sans craft → getInvSizeToCraft renvoie 0
        `when`(gatheringTaskService.openTasksFor(listOf("mob"), mapOf("mob" to 20)))
            .thenReturn(listOf(task("red_slimeball", "mob")))
        stubMaterial("red_slimeball", invSize = 0)

        // when : ne doit pas lever d'ArithmeticException (division par zéro)
        service.workOpenTasks(character, listOf("mob"), mapOf("mob" to 20), allowFight = true)

        // then : chunk = levelingGatherChunkSize(0, remaining=50, 100) = 50 (la quantité restante)
        verify(gatheringTaskService).produceOpenSlices(eqObject("red_slimeball"), eqObject("Kepo"), eq(50), anyObject())
    }

    @Test
    fun `une tranche accordee est collectee puis deposee en banque et validee par produceOpenSlices`() {
        // given : le pool accorde une tranche de 10 via la lambda de production
        `when`(gatheringTaskService.openTasksFor(listOf("mining"), mapOf("mining" to 20)))
            .thenReturn(listOf(task("iron_ore", "mining")))
        stubMaterial("iron_ore", invSize = 2)
        `when`(gatheringTaskService.produceOpenSlices(anyString(), anyString(), anyInt(), anyObject()))
            .thenAnswer { invocation ->
                invocation.getArgument<(Int) -> Unit>(3).invoke(10)
                10
            }

        // when
        service.workOpenTasks(character, listOf("mining"), mapOf("mining" to 20))

        // then : collecte sans entraînement, puis dépôt en banque
        verify(gatheringService)
            .craftOrGather(anyObject(), eqObject("iron_ore"), eq(10), anyInt(), eq(false), eq(false))
        verify(movementService, atLeastOnce()).moveToBank(anyObject(), anyBoolean())
        verify(bankService, atLeastOnce()).emptyInventory(anyObject())
    }

    @Test
    fun `allowFight est propage a la production pour les materiaux mob`() {
        // given : les drops de mob n'ont pas de craft → footprint 0
        `when`(gatheringTaskService.openTasksFor(listOf("mob"), mapOf("mob" to 20)))
            .thenReturn(listOf(task("red_slimeball", "mob")))
        stubMaterial("red_slimeball", invSize = 0)
        `when`(gatheringTaskService.produceOpenSlices(anyString(), anyString(), anyInt(), anyObject()))
            .thenAnswer { invocation ->
                invocation.getArgument<(Int) -> Unit>(3).invoke(5)
                5
            }

        // when
        service.workOpenTasks(character, listOf("mob"), mapOf("mob" to 20), allowFight = true)

        // then
        verify(gatheringService)
            .craftOrGather(anyObject(), eqObject("red_slimeball"), eq(5), anyInt(), eq(true), eq(false))
    }

    @Test
    fun `le chunk vaut au moins 1 pour les materiaux plus volumineux que l'inventaire`() {
        // given : footprint 200 > inventaire 100 → chunk forcé à 1
        `when`(gatheringTaskService.openTasksFor(listOf("mining"), mapOf("mining" to 20)))
            .thenReturn(listOf(task("iron_ore", "mining")))
        stubMaterial("iron_ore", invSize = 200)

        // when
        service.workOpenTasks(character, listOf("mining"), mapOf("mining" to 20))

        // then
        verify(gatheringTaskService).produceOpenSlices(eqObject("iron_ore"), eqObject("Kepo"), eq(1), anyObject())
    }

    @Test
    fun `une tache en echec n'empeche pas la production des suivantes`() {
        // given : la production de la première tâche échoue (combat perdu)
        `when`(gatheringTaskService.openTasksFor(listOf("mining"), mapOf("mining" to 20)))
            .thenReturn(listOf(task("iron_ore", "mining"), task("copper_ore", "mining")))
        stubMaterial("iron_ore", invSize = 2)
        stubMaterial("copper_ore", invSize = 2)
        `when`(gatheringTaskService.produceOpenSlices(eqObject("iron_ore"), anyString(), anyInt(), anyObject()))
            .thenThrow(BattleLostException("chicken"))

        // when : aucune exception ne remonte au job
        val result = service.workOpenTasks(character, listOf("mining"), mapOf("mining" to 20))

        // then : la deuxième tâche est quand même présentée au pool
        verify(gatheringTaskService).produceOpenSlices(eqObject("copper_ore"), eqObject("Kepo"), eq(50), anyObject())
        // et seule la production réussie (copper_ore) compte dans le total
        assertEquals(0, result.produced)
    }

    @Test
    fun `le total produit cumule les retours de produceOpenSlices sur toutes les taches`() {
        // given : deux tâches, chacune produit une tranche
        `when`(gatheringTaskService.openTasksFor(listOf("mining"), mapOf("mining" to 20)))
            .thenReturn(listOf(task("iron_ore", "mining"), task("copper_ore", "mining")))
        stubMaterial("iron_ore", invSize = 2)
        stubMaterial("copper_ore", invSize = 2)
        `when`(gatheringTaskService.produceOpenSlices(eqObject("iron_ore"), anyString(), anyInt(), anyObject()))
            .thenReturn(10)
        `when`(gatheringTaskService.produceOpenSlices(eqObject("copper_ore"), anyString(), anyInt(), anyObject()))
            .thenReturn(7)

        // when
        val result = service.workOpenTasks(character, listOf("mining"), mapOf("mining" to 20))

        // then
        assertEquals(17, result.produced)
    }

    @Test
    fun `une InterruptedException remonte immediatement sans traiter les taches suivantes`() {
        // given : la première tâche est interrompue (mission de priorité supérieure)
        `when`(gatheringTaskService.openTasksFor(listOf("mining"), mapOf("mining" to 20)))
            .thenReturn(listOf(task("iron_ore", "mining"), task("copper_ore", "mining")))
        stubMaterial("iron_ore", invSize = 2)
        stubMaterial("copper_ore", invSize = 2)
        `when`(gatheringTaskService.produceOpenSlices(eqObject("iron_ore"), anyString(), anyInt(), anyObject()))
            .thenAnswer { throw InterruptedException("interrupted") }

        // when - then : l'interruption remonte, la deuxième tâche n'est jamais présentée au pool
        org.junit.jupiter.api.Assertions.assertThrows(InterruptedException::class.java) {
            service.workOpenTasks(character, listOf("mining"), mapOf("mining" to 20))
        }
        verify(gatheringTaskService, never())
            .produceOpenSlices(eqObject("copper_ore"), anyString(), anyInt(), anyObject())
    }

    @Test
    fun `une InterruptedIOException restaure le flag d'interruption et remonte`() {
        // given
        `when`(gatheringTaskService.openTasksFor(listOf("mining"), mapOf("mining" to 20)))
            .thenReturn(listOf(task("iron_ore", "mining"), task("copper_ore", "mining")))
        stubMaterial("iron_ore", invSize = 2)
        stubMaterial("copper_ore", invSize = 2)
        `when`(gatheringTaskService.produceOpenSlices(eqObject("iron_ore"), anyString(), anyInt(), anyObject()))
            .thenAnswer { throw java.io.InterruptedIOException("timed out") }

        // when
        org.junit.jupiter.api.Assertions.assertThrows(java.io.InterruptedIOException::class.java) {
            service.workOpenTasks(character, listOf("mining"), mapOf("mining" to 20))
        }

        // then : le flag d'interruption du thread est restauré et la deuxième tâche n'est pas tentée
        assertTrue(Thread.interrupted())
        verify(gatheringTaskService, never())
            .produceOpenSlices(eqObject("copper_ore"), anyString(), anyInt(), anyObject())
    }

    @Test
    fun `hasOpenTasks reflete l'etat du pool`() {
        // given
        `when`(gatheringTaskService.openTasksFor(listOf("mining"), mapOf("mining" to 20)))
            .thenReturn(listOf(task("iron_ore", "mining")))
        `when`(gatheringTaskService.openTasksFor(listOf("woodcutting"), mapOf("woodcutting" to 20)))
            .thenReturn(emptyList())

        // when - then
        assertTrue(service.hasOpenTasks(listOf("mining"), mapOf("mining" to 20)))
        assertFalse(service.hasOpenTasks(listOf("woodcutting"), mapOf("woodcutting" to 20)))
    }

    private fun character(inventoryMaxItems: Int) = ArtifactsCharacter(
        name = "Kepo", account = "tellenn", level = 20, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = arrayOf(), cooldown = 0, skin = null, task = null,
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0, criticalStrike = 0, speed = 0,
        haste = 0, xp = 0, maxXp = 100, taskType = null, taskTotal = 0, taskProgress = 0,
        miningXp = 0, miningMaxXp = 100, miningLevel = 20, woodcuttingXp = 0, woodcuttingMaxXp = 100,
        woodcuttingLevel = 1, fishingXp = 0, fishingMaxXp = 100, fishingLevel = 1,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 100, weaponcraftingLevel = 1,
        gearcraftingXp = 0, gearcraftingMaxXp = 100, gearcraftingLevel = 1,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 100, jewelrycraftingLevel = 1,
        cookingXp = 0, cookingMaxXp = 100, cookingLevel = 1, alchemyXp = 0, alchemyMaxXp = 100, alchemyLevel = 1,
        inventoryMaxItems = inventoryMaxItems, attackFire = 0, attackEarth = 0, attackWater = 0, attackAir = 0,
        dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0, resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
        weaponSlot = null, runeSlot = null, shieldSlot = null, helmetSlot = null, bodyArmorSlot = null,
        legArmorSlot = null, bootsSlot = null, ring1Slot = null, ring2Slot = null, amuletSlot = null,
        artifact1Slot = null, artifact2Slot = null, artifact3Slot = null,
        utility1Slot = "", utility1SlotQuantity = 0, utility2Slot = "", utility2SlotQuantity = 0,
        bagSlot = null, cooldownExpiration = Instant.now(),
    )
}
