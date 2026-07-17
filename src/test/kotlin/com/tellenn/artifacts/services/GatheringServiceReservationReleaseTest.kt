package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CraftingClient
import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.RecipeIngredient
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
 * Helpers null-safe pour les matchers Mockito sur des paramètres Kotlin non-nullables :
 * `Mockito.any()` renvoie `null`, ce que l'assertion de non-nullité Kotlin rejette.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

class GatheringServiceReservationReleaseTest {

    private lateinit var bankService: BankService
    private lateinit var itemService: ItemService
    private lateinit var movementService: MovementService
    private lateinit var characterService: CharacterService
    private lateinit var battleService: BattleService
    private lateinit var gatheringService: GatheringService

    private val character = character()

    @BeforeEach
    fun setUp() {
        bankService = mock(BankService::class.java)
        itemService = mock(ItemService::class.java)
        movementService = mock(MovementService::class.java)
        characterService = mock(CharacterService::class.java)
        battleService = mock(BattleService::class.java)

        gatheringService = GatheringService(
            gatheringClient = mock(GatheringClient::class.java),
            mapService = mock(MapService::class.java),
            movementService = movementService,
            bankService = bankService,
            characterService = characterService,
            craftingClient = mock(CraftingClient::class.java),
            resourceService = mock(ResourceService::class.java),
            itemService = itemService,
            battleService = battleService,
            equipmentService = mock(EquipmentService::class.java),
            accountClient = mock(AccountClient::class.java),
            npcClient = mock(NpcClient::class.java),
            grandExchangeService = mock(GrandExchangeService::class.java),
            gatheringTaskService = mock(GatheringTaskService::class.java),
            materialResponsibility = mock(MaterialResponsibility::class.java),
            characterContextService = mock(CharacterContextService::class.java),
        )

        `when`(movementService.moveToBank(anyObject(), anyBoolean())).thenReturn(character)
        `when`(bankService.emptyInventory(anyObject())).thenReturn(character)
        `when`(itemService.getInvSizeToCraft(anyObject())).thenReturn(1)
        // Par défaut, les monstres qui droppent les ingrédients sont présents sur la carte
        `when`(battleService.isMonsterForItemOnMap(anyString())).thenReturn(true)
    }

    @Test
    fun `craftOrGather libere les reservations du personnage quand la resolution de la recette echoue`() {
        // given — une recette dont le premier ingrédient est en banque (réservé) et le second
        // exige un combat interdit (allowFight = false) → la résolution échoue en cours de route
        `when`(itemService.getItem("iron_dagger")).thenReturn(
            item("iron_dagger", craft = ItemCraft("weaponcrafting", 10, listOf(
                RecipeIngredient("iron_bar", 2),
                RecipeIngredient("ogre_eye", 1),
            ), 1))
        )
        `when`(itemService.getItem("iron_bar")).thenReturn(item("iron_bar"))
        `when`(itemService.getItem("ogre_eye")).thenReturn(item("ogre_eye", subtype = "mob"))
        `when`(bankService.availableQuantity("iron_bar")).thenReturn(2)

        // when
        assertThrows(IllegalArgumentException::class.java) {
            gatheringService.craftOrGather(character, "iron_dagger", 1, allowFight = false)
        }

        // then — la réservation d'iron_bar ne doit pas survivre à l'échec
        verify(bankService).reserveInBank("iron_bar", 2, "Renoir")
        verify(bankService).releaseAllReservations("Renoir")
    }

    @Test
    fun `craftOrGather ne libere rien quand la resolution reussit`() {
        // given — le cas trivial du wooden_stick se résout sans recette ni réservation
        `when`(itemService.getItem("wooden_stick")).thenReturn(item("wooden_stick"))
        `when`(characterService.unequip(anyObject(), anyString(), anyInt())).thenReturn(character)

        // when
        gatheringService.craftOrGather(character, "wooden_stick", 1)

        // then
        verify(bankService, never()).releaseAllReservations(anyString())
    }

    private fun item(code: String, subtype: String = "bar", craft: ItemCraft? = null) = ItemDetails(
        code = code, name = code, description = "", type = "resource", subtype = subtype,
        level = 10, tradeable = true, craft = craft, effects = emptyList(), conditions = emptyList()
    )

    private fun character() = ArtifactsCharacter(
        name = "Renoir", account = "tellenn", level = 20, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = arrayOf(), cooldown = 0, skin = null, task = null,
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0, criticalStrike = 0, speed = 0,
        haste = 0, xp = 0, maxXp = 100, taskType = null, taskTotal = 0, taskProgress = 0,
        miningXp = 0, miningMaxXp = 100, miningLevel = 20, woodcuttingXp = 0, woodcuttingMaxXp = 100,
        woodcuttingLevel = 1, fishingXp = 0, fishingMaxXp = 100, fishingLevel = 1,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 100, weaponcraftingLevel = 20,
        gearcraftingXp = 0, gearcraftingMaxXp = 100, gearcraftingLevel = 1,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 100, jewelrycraftingLevel = 1,
        cookingXp = 0, cookingMaxXp = 100, cookingLevel = 1,
        alchemyXp = 0, alchemyMaxXp = 100, alchemyLevel = 1,
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
