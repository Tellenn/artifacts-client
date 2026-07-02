package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.RecipeIngredient
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.GatheringService
import com.tellenn.artifacts.services.ItemService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.TaskService
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant

/**
 * Helpers null-safe pour les matchers Mockito sur des paramètres Kotlin non-nullables.
 * `Mockito.any()` renvoie `null`, ce que l'assertion de non-nullité Kotlin rejette ;
 * on enregistre le matcher puis on renvoie une valeur non vérifiée.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

class GenericJobCraftBasicMaterialTest {

    private lateinit var movementService: MovementService
    private lateinit var bankService: BankService
    private lateinit var itemService: ItemService
    private lateinit var gatheringService: GatheringService
    private lateinit var bankItemSyncService: BankItemSyncService
    private lateinit var job: GenericJob
    private lateinit var character: ArtifactsCharacter

    @BeforeEach
    fun setUp() {
        movementService = mock(MovementService::class.java)
        bankService = mock(BankService::class.java)
        itemService = mock(ItemService::class.java)
        gatheringService = mock(GatheringService::class.java)
        bankItemSyncService = mock(BankItemSyncService::class.java)
        character = character(inventoryMaxItems = 105)

        job = GenericJob(
            mapService = mock(MapService::class.java),
            movementService = movementService,
            bankService = bankService,
            characterService = mock(CharacterService::class.java),
            accountClient = mock(AccountClient::class.java),
            taskService = mock(TaskService::class.java),
        )
    }

    @Test
    fun `should cap craft quantity so it fits in the inventory`() {
        // given — iron_bar occupe 5 slots d'inventaire par unité à crafter,
        // la banque a largement assez de minerai pour ne pas être le facteur limitant
        val ironBar = item("iron_bar", craft = ItemCraft(
            skill = "mining", level = 10,
            items = listOf(RecipeIngredient("iron_ore", 5)), quantity = 1,
        ))
        `when`(itemService.getAllCraftableItemsBySkillAndSubtypeAndMaxLevel(anyString(), anyString(), anyInt()))
            .thenReturn(listOf(ironBar))
        `when`(itemService.getInvSizeToCraft(anyObject())).thenReturn(5)
        `when`(bankService.canCraftFromBank(anyObject(), anyInt())).thenReturn(true)
        `when`(bankService.getOne("iron_ore")).thenReturn(SimpleItem("iron_ore", 10_000))
        `when`(movementService.moveToBank(anyObject(), anyBoolean())).thenReturn(character)
        `when`(bankService.emptyInventory(anyObject())).thenReturn(character)

        val craftedQuantities = mutableListOf<Int>()
        `when`(gatheringService.craftOrGather(anyObject(), anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
            .thenAnswer { invocation ->
                craftedQuantities.add(invocation.getArgument(2) as Int)
                character
            }

        // when
        job.craftBasicMaterialFromBank("mining", "bar", itemService, gatheringService, bankItemSyncService, character)

        // then — (105 - 10) / 5 = 19 unités max pour tenir dans l'inventaire
        assertEquals(listOf(19), craftedQuantities)
    }

    private fun item(code: String, craft: ItemCraft?) = ItemDetails(
        code = code, name = code, description = "", type = "resource", subtype = "bar",
        level = 10, tradeable = true, recyclable = false, craft = craft, effects = null, conditions = null,
    )

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
