package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.BankItemTransaction
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant

class BankServiceReservationTest {

    private val bankClient = mock(BankClient::class.java)
    private val bankRepository = mock(BankItemRepository::class.java)
    private val itemRepository = mock(ItemRepository::class.java)
    private val characterService = mock(CharacterService::class.java)
    private val bankItemSyncService = mock(BankItemSyncService::class.java)
    private val accountClient = mock(AccountClient::class.java)
    private val movementClient = mock(MovementClient::class.java)
    private val mapService = mock(MapService::class.java)
    private val teleportService = mock(TeleportService::class.java)

    private val service = BankService(
        bankClient, bankRepository, itemRepository, characterService,
        bankItemSyncService, accountClient, movementClient, mapService, teleportService
    )

    @Test
    fun `isInBank deduit les reservations de tous les personnages`() {
        // given — 10 iron_ore en banque, 5 réservés par Renoir et 3 par Kepo
        stubBankedQuantity("iron_ore", 10)
        service.reserveInBank("iron_ore", 5, "Renoir")
        service.reserveInBank("iron_ore", 3, "Kepo")

        // when / then — seuls 2 restent disponibles
        assertTrue(service.isInBank("iron_ore", 2))
        assertFalse(service.isInBank("iron_ore", 3))
    }

    @Test
    fun `releaseAllReservations ne libere que les reservations du personnage donne`() {
        // given
        stubBankedQuantity("iron_ore", 10)
        service.reserveInBank("iron_ore", 5, "Renoir")
        service.reserveInBank("iron_ore", 3, "Kepo")

        // when
        service.releaseAllReservations("Renoir")

        // then — la réservation de Kepo tient toujours
        assertTrue(service.isInBank("iron_ore", 7))
        assertFalse(service.isInBank("iron_ore", 8))
    }

    @Test
    fun `withdrawMany libere la reservation du personnage qui retire`() {
        // given — Renoir a réservé 5 iron_ore puis les retire effectivement
        stubBankedQuantity("iron_ore", 10)
        service.reserveInBank("iron_ore", 5, "Renoir")
        val character = buildCharacter()
        val items = ArrayList(listOf(SimpleItem("iron_ore", 5)))
        `when`(bankClient.withdrawItems("Renoir", items))
            .thenReturn(ArtifactsResponseBody(transaction(character)))

        // when
        service.withdrawMany(items, character)

        // then — plus aucune réservation fantôme
        assertTrue(service.isInBank("iron_ore", 10))
    }

    private fun stubBankedQuantity(code: String, quantity: Int) {
        `when`(bankClient.getBankedItems(code))
            .thenReturn(ArtifactsArrayResponseBody(listOf(SimpleItem(code, quantity)), 1, 1, 1, 1))
    }

    private fun transaction(character: ArtifactsCharacter) = BankItemTransaction(
        cooldown = Cooldown(0, 0, Instant.now(), Instant.now(), "withdraw"),
        character = character,
        items = emptyList(),
        bank = emptyList(),
    )

    private fun buildCharacter(): ArtifactsCharacter = ArtifactsCharacter(
        name = "Renoir", account = "tellenn", level = 30, gold = 0,
        hp = 100, maxHp = 100, x = 0, y = 0, mapId = 545, layer = "main",
        inventory = emptyArray(), cooldown = 0, skin = null, task = null,
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0,
        criticalStrike = 0, speed = 0, haste = 0, xp = 0, maxXp = 0,
        taskType = null, taskTotal = 0, taskProgress = 0,
        miningXp = 0, miningMaxXp = 0, miningLevel = 0,
        woodcuttingXp = 0, woodcuttingMaxXp = 0, woodcuttingLevel = 0,
        fishingXp = 0, fishingMaxXp = 0, fishingLevel = 0,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 0, weaponcraftingLevel = 0,
        gearcraftingXp = 0, gearcraftingMaxXp = 0, gearcraftingLevel = 0,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 0, jewelrycraftingLevel = 0,
        cookingXp = 0, cookingMaxXp = 0, cookingLevel = 0,
        alchemyXp = 0, alchemyMaxXp = 0, alchemyLevel = 0,
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
