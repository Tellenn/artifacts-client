package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.UseItemResponseBody
import com.tellenn.artifacts.db.clients.MapMongoClient
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.*
import com.tellenn.artifacts.models.Achievement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class TeleportServiceTest {

    private val characterClient = mock(CharacterClient::class.java)
    private val itemRepository = mock(ItemRepository::class.java)
    private val bankItemRepository = mock(BankItemRepository::class.java)
    private val accountClient = mock(AccountClient::class.java)
    private val mapMongoClient = mock(MapMongoClient::class.java)

    private val service = TeleportService(
        characterClient, itemRepository, bankItemRepository, accountClient, mapMongoClient
    )

    private fun buildCharacter(
        name: String = "Cloud",
        account: String = "tellenn",
        level: Int = 20,
        alchemyLevel: Int = 0,
        mapId: Int = 1,
        inventory: Array<InventorySlot> = emptyArray(),
    ): ArtifactsCharacter = ArtifactsCharacter(
        name = name, account = account, level = level, gold = 0,
        hp = 100, maxHp = 100, x = 0, y = 0, mapId = mapId, layer = "main",
        inventory = inventory, cooldown = 0, skin = null, task = null,
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
        alchemyXp = 0, alchemyMaxXp = 0, alchemyLevel = alchemyLevel,
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

    private fun buildPotion(code: String, destinationMapId: Int, conditions: List<ItemCondition>? = null): ItemDetails =
        ItemDetails(
            code = code, name = code, description = "",
            type = "consumable", subtype = "potion", level = 1,
            tradeable = true, recyclable = false, craft = null,
            effects = listOf(Effect("teleport", destinationMapId, null)),
            conditions = conditions,
        )

    private fun buildBankDoc(code: String, quantity: Int = 5): BankItemDocument =
        BankItemDocument(
            code = code, name = code, description = "",
            type = "consumable", subtype = "potion", level = 1,
            tradeable = true, recyclable = false,
            effects = listOf(Effect("teleport", 545, null)),
            craft = null, conditions = null, quantity = quantity,
        )

    @Test
    fun `canUse returns true when item has no conditions`() {
        val potion = buildPotion("tp_potion", 545)
        assertTrue(service.canUse(buildCharacter(), potion))
    }

    @Test
    fun `canUse returns true when achievement condition is met`() {
        val potion = buildPotion("tp_potion", 545,
            conditions = listOf(ItemCondition("draconic_harvest", "achievement_unlocked", 1)))
        val character = buildCharacter()
        val achievement = mock(Achievement::class.java)
        `when`(achievement.code).thenReturn("draconic_harvest")
        `when`(accountClient.getAccountAchievements(character.account, true))
            .thenReturn(ArtifactsArrayResponseBody(listOf(achievement), 1, 1, 50, 1))
        assertTrue(service.canUse(character, potion))
    }

    @Test
    fun `canUse returns false when achievement condition is not met`() {
        val potion = buildPotion("tp_potion", 545,
            conditions = listOf(ItemCondition("draconic_harvest", "achievement_unlocked", 1)))
        val character = buildCharacter()
        `when`(accountClient.getAccountAchievements(character.account, true))
            .thenReturn(ArtifactsArrayResponseBody(emptyList(), 0, 1, 50, 0))
        assertFalse(service.canUse(character, potion))
    }

    @Test
    fun `canUse returns true when stat gt condition is met`() {
        val potion = buildPotion("tp_potion", 545,
            conditions = listOf(ItemCondition("level", "gt", 10)))
        assertTrue(service.canUse(buildCharacter(level = 20), potion))
    }

    @Test
    fun `canUse returns false when stat gt condition is not met`() {
        val potion = buildPotion("tp_potion", 545,
            conditions = listOf(ItemCondition("level", "gt", 30)))
        assertFalse(service.canUse(buildCharacter(level = 20), potion))
    }

    @Test
    fun `canUse returns false for cost condition`() {
        val potion = buildPotion("tp_potion", 545,
            conditions = listOf(ItemCondition("gold", "cost", 100)))
        assertFalse(service.canUse(buildCharacter(), potion))
    }

    // ── findUsableTeleportPotionsInInventory ───────────────────────────────

    @Test
    fun `findUsableTeleportPotionsInInventory returns empty when inventory is empty`() {
        val result = service.findUsableTeleportPotionsInInventory(buildCharacter(inventory = emptyArray()))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findUsableTeleportPotionsInInventory returns potion with destination mapId`() {
        val character = buildCharacter(inventory = arrayOf(InventorySlot(1, "tp_potion", 1)))
        val potion = buildPotion("tp_potion", 545)
        `when`(itemRepository.findByCodeIn(listOf("tp_potion"))).thenReturn(listOf(potion))

        val result = service.findUsableTeleportPotionsInInventory(character)

        assertEquals(1, result.size)
        assertEquals("tp_potion", result[0].first.code)
        assertEquals(545, result[0].second)
    }

    @Test
    fun `findUsableTeleportPotionsInInventory excludes non-teleport items`() {
        val character = buildCharacter(inventory = arrayOf(InventorySlot(1, "health_potion", 1)))
        val healthPotion = ItemDetails(
            code = "health_potion", name = "Health Potion", description = "",
            type = "consumable", subtype = "potion", level = 1,
            tradeable = true, recyclable = false, craft = null,
            effects = listOf(Effect("restore", 50, null)), conditions = null,
        )
        `when`(itemRepository.findByCodeIn(listOf("health_potion"))).thenReturn(listOf(healthPotion))

        val result = service.findUsableTeleportPotionsInInventory(character)

        assertTrue(result.isEmpty())
    }

    // ── findPotionForDestination ───────────────────────────────────────────

    @Test
    fun `findPotionForDestination returns exact match`() {
        val character = buildCharacter(inventory = arrayOf(InventorySlot(1, "tp_potion", 1)))
        val potion = buildPotion("tp_potion", 545)
        `when`(itemRepository.findByCodeIn(listOf("tp_potion"))).thenReturn(listOf(potion))

        val result = service.findPotionForDestination(character, 545)

        assertEquals("tp_potion", result?.code)
    }

    @Test
    fun `findPotionForDestination returns regional match when no exact match`() {
        val character = buildCharacter(inventory = arrayOf(InventorySlot(1, "tp_potion", 1)))
        val potion = buildPotion("tp_potion", 545)
        `when`(itemRepository.findByCodeIn(listOf("tp_potion"))).thenReturn(listOf(potion))
        `when`(mapMongoClient.getMapById(600)).thenReturn(
            MapData("Target", "skin", 0, 0, 600, "main", null,
                Interactions(MapContent("resource", "iron"), null), region = 1)
        )
        `when`(mapMongoClient.getMapById(545)).thenReturn(
            MapData("Near", "skin", 0, 0, 545, "main", null,
                Interactions(MapContent("resource", "coal"), null), region = 1)
        )

        val result = service.findPotionForDestination(character, 600)

        assertEquals("tp_potion", result?.code)
    }

    @Test
    fun `findPotionForDestination picks the regional potion landing closest to the target`() {
        val character = buildCharacter(mapId = 1, inventory = arrayOf(
            InventorySlot(1, "tp_far", 1),
            InventorySlot(2, "tp_near", 1),
        ))
        `when`(itemRepository.findByCodeIn(listOf("tp_far", "tp_near")))
            .thenReturn(listOf(buildPotion("tp_far", 700), buildPotion("tp_near", 545)))
        // Personnage hors région cible : les deux potions rapprochent, on prend la plus proche.
        `when`(mapMongoClient.getMapById(1)).thenReturn(
            MapData("Origin", "skin", 0, 0, 1, "main", null,
                Interactions(MapContent("resource", "iron"), null), region = 2))
        `when`(mapMongoClient.getMapById(600)).thenReturn(
            MapData("Target", "skin", 10, 0, 600, "main", null,
                Interactions(MapContent("resource", "iron"), null), region = 1))
        `when`(mapMongoClient.getMapById(700)).thenReturn(
            MapData("Far", "skin", 0, 0, 700, "main", null,
                Interactions(MapContent("resource", "coal"), null), region = 1))
        `when`(mapMongoClient.getMapById(545)).thenReturn(
            MapData("Near", "skin", 9, 0, 545, "main", null,
                Interactions(MapContent("resource", "coal"), null), region = 1))

        val result = service.findPotionForDestination(character, 600)

        assertEquals("tp_near", result?.code)
    }

    @Test
    fun `findPotionForDestination uses regional potion when it lands closer to the target`() {
        val character = buildCharacter(mapId = 1, inventory = arrayOf(InventorySlot(1, "tp_potion", 1)))
        val potion = buildPotion("tp_potion", 545)
        `when`(itemRepository.findByCodeIn(listOf("tp_potion"))).thenReturn(listOf(potion))
        // Personnage déjà dans la région cible, loin de la cible (0,0)->(20,0).
        `when`(mapMongoClient.getMapById(1)).thenReturn(
            MapData("Origin", "skin", 0, 0, 1, "main", null,
                Interactions(MapContent("resource", "iron"), null), region = 1))
        `when`(mapMongoClient.getMapById(600)).thenReturn(
            MapData("Target", "skin", 20, 0, 600, "main", null,
                Interactions(MapContent("resource", "iron"), null), region = 1))
        // La potion atterrit en (18,0) : plus proche que la position actuelle.
        `when`(mapMongoClient.getMapById(545)).thenReturn(
            MapData("Near", "skin", 18, 0, 545, "main", null,
                Interactions(MapContent("resource", "coal"), null), region = 1))

        val result = service.findPotionForDestination(character, 600)

        assertEquals("tp_potion", result?.code)
    }

    @Test
    fun `findPotionForDestination ignores regional potion that would land further from the target`() {
        val character = buildCharacter(mapId = 1, inventory = arrayOf(InventorySlot(1, "tp_potion", 1)))
        val potion = buildPotion("tp_potion", 545)
        `when`(itemRepository.findByCodeIn(listOf("tp_potion"))).thenReturn(listOf(potion))
        // Personnage déjà dans la région cible, proche de la cible (0,0)->(2,0).
        `when`(mapMongoClient.getMapById(1)).thenReturn(
            MapData("Origin", "skin", 0, 0, 1, "main", null,
                Interactions(MapContent("resource", "iron"), null), region = 1))
        `when`(mapMongoClient.getMapById(600)).thenReturn(
            MapData("Target", "skin", 2, 0, 600, "main", null,
                Interactions(MapContent("resource", "iron"), null), region = 1))
        // La potion atterrit en (50,0) : bien plus loin -> ne pas l'utiliser.
        `when`(mapMongoClient.getMapById(545)).thenReturn(
            MapData("Far", "skin", 50, 0, 545, "main", null,
                Interactions(MapContent("resource", "coal"), null), region = 1))

        val result = service.findPotionForDestination(character, 600)

        assertNull(result)
    }

    @Test
    fun `findPotionForDestination returns null when no match`() {
        val character = buildCharacter(inventory = arrayOf(InventorySlot(1, "tp_potion", 1)))
        val potion = buildPotion("tp_potion", 545)
        `when`(itemRepository.findByCodeIn(listOf("tp_potion"))).thenReturn(listOf(potion))
        `when`(mapMongoClient.getMapById(999)).thenReturn(
            MapData("Far", "skin", 0, 0, 999, "main", null,
                Interactions(MapContent("resource", "coal"), null), region = 2)
        )
        `when`(mapMongoClient.getMapById(545)).thenReturn(
            MapData("Near", "skin", 0, 0, 545, "main", null,
                Interactions(MapContent("resource", "coal"), null), region = 1)
        )

        val result = service.findPotionForDestination(character, 999)

        assertNull(result)
    }

    // ── findUsableTeleportPotionsInBank ────────────────────────────────────

    @Test
    fun `findUsableTeleportPotionsInBank returns every distinct usable teleport potion`() {
        val character = buildCharacter()
        val recallDoc = buildBankDoc("recall_potion", quantity = 4)
        val regionDoc = buildBankDoc("region_potion", quantity = 2)
        `when`(bankItemRepository.findByEffectsCode("teleport")).thenReturn(listOf(recallDoc, regionDoc))
        `when`(itemRepository.findByCode("recall_potion")).thenReturn(buildPotion("recall_potion", 271))
        `when`(itemRepository.findByCode("region_potion")).thenReturn(buildPotion("region_potion", 600))

        val result = service.findUsableTeleportPotionsInBank(character)

        assertEquals(setOf("recall_potion", "region_potion"), result.map { it.code }.toSet())
        assertEquals(2, result.first { it.code == "recall_potion" }.quantity)
        assertEquals(1, result.first { it.code == "region_potion" }.quantity)
    }

    @Test
    fun `findUsableTeleportPotionsInBank withdraws a single recall potion when only one is in stock`() {
        val character = buildCharacter()
        `when`(bankItemRepository.findByEffectsCode("teleport"))
            .thenReturn(listOf(buildBankDoc("recall_potion", quantity = 1)))
        `when`(itemRepository.findByCode("recall_potion")).thenReturn(buildPotion("recall_potion", 271))

        val result = service.findUsableTeleportPotionsInBank(character)

        assertEquals(1, result.first { it.code == "recall_potion" }.quantity)
    }

    @Test
    fun `findUsableTeleportPotionsInBank excludes potions whose conditions are not met`() {
        val character = buildCharacter(level = 10)
        val usableDoc = buildBankDoc("recall_potion", quantity = 1)
        val lockedDoc = buildBankDoc("elite_potion", quantity = 1)
        `when`(bankItemRepository.findByEffectsCode("teleport")).thenReturn(listOf(usableDoc, lockedDoc))
        `when`(itemRepository.findByCode("recall_potion")).thenReturn(buildPotion("recall_potion", 271))
        `when`(itemRepository.findByCode("elite_potion")).thenReturn(
            buildPotion("elite_potion", 600, conditions = listOf(ItemCondition("level", "gt", 50))))

        val result = service.findUsableTeleportPotionsInBank(character)

        assertEquals(listOf("recall_potion"), result.map { it.code })
    }

    @Test
    fun `findUsableTeleportPotionsInBank returns empty when bank has no teleport potions`() {
        val character = buildCharacter()
        `when`(bankItemRepository.findByEffectsCode("teleport")).thenReturn(emptyList())

        val result = service.findUsableTeleportPotionsInBank(character)

        assertTrue(result.isEmpty())
    }

    // ── use ───────────────────────────────────────────────────────────────

    @Test
    fun `use calls characterClient useItem and returns updated character`() {
        val character = buildCharacter()
        val updatedCharacter = buildCharacter(mapId = 545)
        val responseBody = mock(UseItemResponseBody::class.java)
        `when`(responseBody.character).thenReturn(updatedCharacter)
        @Suppress("UNCHECKED_CAST")
        val response = mock(ArtifactsResponseBody::class.java) as ArtifactsResponseBody<UseItemResponseBody>
        `when`(response.data).thenReturn(responseBody)
        `when`(characterClient.useItem("Cloud", "tp_bank", 1)).thenReturn(response)

        val result = service.use(character, "tp_bank")

        assertEquals(545, result.mapId)
        verify(characterClient).useItem("Cloud", "tp_bank", 1)
    }
}
