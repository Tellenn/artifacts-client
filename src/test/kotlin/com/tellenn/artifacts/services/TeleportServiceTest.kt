package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.UseItemResponseBody
import com.tellenn.artifacts.db.clients.MapMongoClient
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class TeleportServiceTest {

    private val characterClient = mock(CharacterClient::class.java)
    private val itemRepository = mock(ItemRepository::class.java)
    private val bankItemRepository = mock(BankItemRepository::class.java)
    private val achievementCacheService = mock(AchievementCacheService::class.java)
    private val mapMongoClient = mock(MapMongoClient::class.java)
    private val mapService = mock(MapService::class.java)

    private val service = TeleportService(
        characterClient, itemRepository, bankItemRepository, achievementCacheService, mapMongoClient, mapService
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
        `when`(achievementCacheService.isUnlocked(character.account, "draconic_harvest")).thenReturn(true)
        assertTrue(service.canUse(character, potion))
    }

    @Test
    fun `canUse returns false when achievement condition is not met`() {
        val potion = buildPotion("tp_potion", 545,
            conditions = listOf(ItemCondition("draconic_harvest", "achievement_unlocked", 1)))
        val character = buildCharacter()
        `when`(achievementCacheService.isUnlocked(character.account, "draconic_harvest")).thenReturn(false)
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

    private fun map(mapId: Int, x: Int, y: Int, region: Int): MapData =
        MapData("map$mapId", "skin", x, y, mapId, "main", null,
            Interactions(MapContent("resource", "iron"), null), region = region)

    @Test
    fun `findPotionForDestination returns null when inventory has no usable potion`() {
        val result = service.findPotionForDestination(buildCharacter(inventory = emptyArray()), 600)
        assertNull(result)
    }

    @Test
    fun `findPotionForDestination returns null when destination map is unknown`() {
        val character = buildCharacter(inventory = arrayOf(InventorySlot(1, "tp_potion", 1)))
        `when`(itemRepository.findByCodeIn(listOf("tp_potion"))).thenReturn(listOf(buildPotion("tp_potion", 545)))
        `when`(mapMongoClient.getMapById(600)).thenReturn(null)

        val result = service.findPotionForDestination(character, 600)

        assertNull(result)
    }

    @Test
    fun `findPotionForDestination takes the potion when it saves more than three cells`() {
        val character = buildCharacter(mapId = 1, inventory = arrayOf(InventorySlot(1, "tp_potion", 1)))
        `when`(itemRepository.findByCodeIn(listOf("tp_potion"))).thenReturn(listOf(buildPotion("tp_potion", 545)))
        // À pied : 20 cases (x=0 -> x=20). Avec la potion (atterrit en x=18) : 2 cases. Économie 18.
        `when`(mapMongoClient.getMapById(1)).thenReturn(map(1, 0, 0, region = 1))
        `when`(mapMongoClient.getMapById(600)).thenReturn(map(600, 20, 0, region = 1))
        `when`(mapMongoClient.getMapById(545)).thenReturn(map(545, 18, 0, region = 1))

        val result = service.findPotionForDestination(character, 600)

        assertEquals("tp_potion", result?.code)
    }

    @Test
    fun `findPotionForDestination rejects the potion when it saves three cells or fewer`() {
        val character = buildCharacter(mapId = 1, inventory = arrayOf(InventorySlot(1, "tp_potion", 1)))
        `when`(itemRepository.findByCodeIn(listOf("tp_potion"))).thenReturn(listOf(buildPotion("tp_potion", 545)))
        // À pied : 5 cases. Avec la potion (atterrit en x=3) : 2 cases. Économie 3 (pas > 3) -> refus.
        `when`(mapMongoClient.getMapById(1)).thenReturn(map(1, 0, 0, region = 1))
        `when`(mapMongoClient.getMapById(600)).thenReturn(map(600, 5, 0, region = 1))
        `when`(mapMongoClient.getMapById(545)).thenReturn(map(545, 3, 0, region = 1))

        val result = service.findPotionForDestination(character, 600)

        assertNull(result)
    }

    @Test
    fun `findPotionForDestination returns the first qualifying potion without checking the others`() {
        val character = buildCharacter(mapId = 1, inventory = arrayOf(
            InventorySlot(1, "tp_first", 1),
            InventorySlot(2, "tp_second", 1),
        ))
        `when`(itemRepository.findByCodeIn(listOf("tp_first", "tp_second")))
            .thenReturn(listOf(buildPotion("tp_first", 700), buildPotion("tp_second", 545)))
        // Les deux économisent > 3 cases ; tp_second est plus proche mais tp_first vient en premier.
        `when`(mapMongoClient.getMapById(1)).thenReturn(map(1, 0, 0, region = 1))
        `when`(mapMongoClient.getMapById(600)).thenReturn(map(600, 20, 0, region = 1))
        `when`(mapMongoClient.getMapById(700)).thenReturn(map(700, 10, 0, region = 1))
        `when`(mapMongoClient.getMapById(545)).thenReturn(map(545, 19, 0, region = 1))

        val result = service.findPotionForDestination(character, 600)

        assertEquals("tp_first", result?.code)
    }

    @Test
    fun `findPotionForDestination counts region transitions in the walking cost`() {
        val character = buildCharacter(mapId = 1, inventory = arrayOf(InventorySlot(1, "tp_potion", 1)))
        `when`(itemRepository.findByCodeIn(listOf("tp_potion"))).thenReturn(listOf(buildPotion("tp_potion", 545)))
        // Même coordonnées mais région différente : à pied = 1 transition * 5 = 5 cases.
        // La potion atterrit dans la bonne région au même point -> 0 case. Économie 5 > 3.
        `when`(mapMongoClient.getMapById(1)).thenReturn(map(1, 0, 0, region = 2))
        `when`(mapMongoClient.getMapById(600)).thenReturn(map(600, 0, 0, region = 1))
        `when`(mapMongoClient.getMapById(545)).thenReturn(map(545, 0, 0, region = 1))
        `when`(mapService.findTransitionPath(2, 1)).thenReturn(listOf(mock(TransitionMapper::class.java)))

        val result = service.findPotionForDestination(character, 600)

        assertEquals("tp_potion", result?.code)
    }

    @Test
    fun `findPotionForDestination rejects an out-of-region potion that does not save enough`() {
        val character = buildCharacter(mapId = 1, inventory = arrayOf(InventorySlot(1, "tp_potion", 1)))
        `when`(itemRepository.findByCodeIn(listOf("tp_potion"))).thenReturn(listOf(buildPotion("tp_potion", 545)))
        // À pied : 1 transition * 5 = 5 cases. La potion rejoint la région cible mais à 4 cases
        // de la cible -> 4 cases. Économie 1 (pas > 3) -> refus malgré le changement de région.
        `when`(mapMongoClient.getMapById(1)).thenReturn(map(1, 0, 0, region = 2))
        `when`(mapMongoClient.getMapById(600)).thenReturn(map(600, 0, 0, region = 1))
        `when`(mapMongoClient.getMapById(545)).thenReturn(map(545, 4, 0, region = 1))
        `when`(mapService.findTransitionPath(2, 1)).thenReturn(listOf(mock(TransitionMapper::class.java)))

        val result = service.findPotionForDestination(character, 600)

        assertNull(result)
    }

    // ── findPotionLandingInRegion ──────────────────────────────────────────

    @Test
    fun `findPotionLandingInRegion returns the potion that lands in the target region`() {
        val character = buildCharacter(inventory = arrayOf(
            InventorySlot(1, "recall_potion", 1),
            InventorySlot(2, "forest_bank_potion", 1),
        ))
        `when`(itemRepository.findByCodeIn(listOf("recall_potion", "forest_bank_potion")))
            .thenReturn(listOf(buildPotion("recall_potion", 271), buildPotion("forest_bank_potion", 955)))
        // recall atterrit region 3, forest_bank region 1 : on veut la région 1.
        `when`(mapMongoClient.getMapById(271)).thenReturn(map(271, 0, 0, region = 3))
        `when`(mapMongoClient.getMapById(955)).thenReturn(map(955, 0, 0, region = 1))

        val result = service.findPotionLandingInRegion(character, 1)

        assertEquals("forest_bank_potion", result?.code)
    }

    @Test
    fun `findPotionLandingInRegion returns null when no potion lands in the target region`() {
        val character = buildCharacter(inventory = arrayOf(InventorySlot(1, "recall_potion", 1)))
        `when`(itemRepository.findByCodeIn(listOf("recall_potion")))
            .thenReturn(listOf(buildPotion("recall_potion", 271)))
        `when`(mapMongoClient.getMapById(271)).thenReturn(map(271, 0, 0, region = 3))

        val result = service.findPotionLandingInRegion(character, 1)

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
