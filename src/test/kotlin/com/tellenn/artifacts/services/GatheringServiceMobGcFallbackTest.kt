package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CraftingClient
import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * enchanted_mushroom est un drop de monstre (subtype "mob") sans recette : Aerith, qui ne combat
 * pas, ne peut l'obtenir qu'au Grand Exchange. La branche "mob" doit donc préférer l'achat GC pour
 * un ingrédient (functionLevel > 0) avant de tomber sur le combat impossible.
 */
class GatheringServiceMobGcFallbackTest {

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T
    private fun <T> anyObject(): T { any<T>(); return uninitialized() }

    private lateinit var bankService: BankService
    private lateinit var itemService: ItemService
    private lateinit var grandExchangeService: GrandExchangeService
    private lateinit var battleService: BattleService
    private lateinit var gatheringService: GatheringService

    private val character = character()

    @BeforeEach
    fun setUp() {
        bankService = mock(BankService::class.java)
        itemService = mock(ItemService::class.java)
        grandExchangeService = mock(GrandExchangeService::class.java)
        battleService = mock(BattleService::class.java)

        gatheringService = GatheringService(
            gatheringClient = mock(GatheringClient::class.java),
            mapService = mock(MapService::class.java),
            movementService = mock(MovementService::class.java),
            bankService = bankService,
            characterService = mock(CharacterService::class.java),
            craftingClient = mock(CraftingClient::class.java),
            resourceService = mock(ResourceService::class.java),
            itemService = itemService,
            battleService = battleService,
            equipmentService = mock(EquipmentService::class.java),
            accountClient = mock(AccountClient::class.java),
            npcClient = mock(NpcClient::class.java),
            grandExchangeService = grandExchangeService,
            gatheringTaskService = mock(GatheringTaskService::class.java),
            materialResponsibility = mock(MaterialResponsibility::class.java),
            characterContextService = mock(CharacterContextService::class.java),
        )

        `when`(itemService.getInvSizeToCraft(anyObject())).thenReturn(0)
        `when`(itemService.getItem("enchanted_mushroom")).thenReturn(mobDrop("enchanted_mushroom"))
        `when`(bankService.availableQuantity("enchanted_mushroom")).thenReturn(0)
    }

    @Test
    fun `buys a mob-drop ingredient from the Grand Exchange instead of fighting`() {
        // given — une offre GC rentable existe pour le drop
        `when`(grandExchangeService.shouldBuyFromGC(anyObject(), anyObject(), anyInt())).thenReturn(true)
        `when`(grandExchangeService.buyFromGC(anyObject(), anyObject(), anyInt())).thenReturn(character)

        // when — collecte d'un ingrédient (functionLevel > 0) sans combat autorisé
        gatheringService.craftOrGather(character, "enchanted_mushroom", 3, functionLevel = 1, allowFight = false)

        // then — on achète au GC, on ne combat jamais
        verify(grandExchangeService).buyFromGC(anyObject(), anyObject(), anyInt())
        verify(battleService, never()).fightToGetItem(anyObject(), anyString(), anyInt(), anyBoolean())
    }

    @Test
    fun `still fails on a mob-drop ingredient when no Grand Exchange offer and fighting disabled`() {
        // given — aucune offre GC (défaut du mock : false)
        // when / then — sans combat ni achat possible, la recette échoue explicitement
        assertThrows(IllegalArgumentException::class.java) {
            gatheringService.craftOrGather(character, "enchanted_mushroom", 3, functionLevel = 1, allowFight = false)
        }
    }

    private fun mobDrop(code: String) = ItemDetails(
        code = code, name = code, description = "", type = "resource", subtype = "mob",
        level = 40, tradeable = true, craft = null, effects = emptyList(), conditions = emptyList()
    )

    private fun character() = ArtifactsCharacter(
        name = "Aerith", account = "tellenn", level = 40, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = arrayOf(), cooldown = 0, skin = null, task = null,
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0, criticalStrike = 0, speed = 0,
        haste = 0, xp = 0, maxXp = 100, taskType = null, taskTotal = 0, taskProgress = 0,
        miningXp = 0, miningMaxXp = 100, miningLevel = 1, woodcuttingXp = 0, woodcuttingMaxXp = 100,
        woodcuttingLevel = 1, fishingXp = 0, fishingMaxXp = 100, fishingLevel = 40,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 100, weaponcraftingLevel = 1,
        gearcraftingXp = 0, gearcraftingMaxXp = 100, gearcraftingLevel = 1,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 100, jewelrycraftingLevel = 1,
        cookingXp = 0, cookingMaxXp = 100, cookingLevel = 1,
        alchemyXp = 0, alchemyMaxXp = 100, alchemyLevel = 40,
        inventoryMaxItems = 100,
        attackFire = 0, attackEarth = 0, attackWater = 0, attackAir = 0,
        dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0,
        resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
        weaponSlot = null, runeSlot = null, shieldSlot = null, helmetSlot = null,
        bodyArmorSlot = null, legArmorSlot = null, bootsSlot = null,
        ring1Slot = null, ring2Slot = null, amuletSlot = null,
        artifact1Slot = null, artifact2Slot = null, artifact3Slot = null,
        utility1Slot = "", utility1SlotQuantity = 0,
        utility2Slot = "", utility2SlotQuantity = 0,
        bagSlot = null, cooldownExpiration = null,
    )
}
