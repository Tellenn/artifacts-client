package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.InventorySlot
import com.tellenn.artifacts.config.MongoTestConfiguration
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Import(MongoTestConfiguration::class)
@Testcontainers
class BankServiceTest {

    private lateinit var bankService: BankService
    private lateinit var bankClient: BankClient

    @Autowired
    private lateinit var bankRepository: BankItemRepository

    @Autowired
    private lateinit var itemRepository: ItemRepository

    @BeforeEach
    fun setup() {
        // Create mock for the client only
        bankClient = Mockito.mock(BankClient::class.java)

        // Clear repositories
        bankRepository.deleteAll()
        itemRepository.deleteAll()

        // Create the service with real repositories and mocked client
        bankService = BankService(bankClient, bankRepository, itemRepository)
    }

    @AfterEach
    fun cleanup() {
        // Clean up after each test
        bankRepository.deleteAll()
        itemRepository.deleteAll()
    }

    @Test
    fun `should insert new item when it doesn't exist in bank`() {
        // Given
        val inventorySlot = InventorySlot(1, "item1", 5)
        val inventoryArray = arrayOf(inventorySlot)
        val character = createTestCharacter(inventoryArray)

        // Add test item to item repository
        val itemDocument = createTestItemDocument("item1", "Test Item")
        itemRepository.save(itemDocument)

        // When
        val result = bankService.emptyInventory(character)

        // Then
        assertEquals(character, result)

        // Verify that a new bank item was created
        val bankItems = bankRepository.findAll()
        assertEquals(1, bankItems.size)
        assertEquals("item1", bankItems[0].code)
        assertEquals(5, bankItems[0].quantity)
    }

    @Test
    fun `should update quantity when item exists in bank`() {
        // Given
        val inventorySlot = InventorySlot(1, "item1", 5)
        val inventoryArray = arrayOf(inventorySlot)
        val character = createTestCharacter(inventoryArray)

        // Add test item to item repository
        val itemDocument = createTestItemDocument("item1", "Test Item")
        itemRepository.save(itemDocument)

        // Add existing bank item
        val existingBankItem = createTestBankItemDocument("item1", "Test Item", 10)
        bankRepository.save(existingBankItem)

        // When
        val result = bankService.emptyInventory(character)

        // Then
        assertEquals(character, result)

        // Verify that the bank item was updated with the new quantity
        val bankItems = bankRepository.findAll()
        assertEquals(1, bankItems.size)
        assertEquals("item1", bankItems[0].code)
        assertEquals(15, bankItems[0].quantity)
    }

    private fun createTestCharacter(inventory: Array<InventorySlot>?): ArtifactsCharacter {
        return ArtifactsCharacter(
            name = "TestCharacter",
            account = "TestAccount",
            level = 1,
            gold = 100,
            hp = 100,
            maxHp = 100,
            x = 0,
            y = 0,
            inventory = inventory,
            cooldown = 0,
            skin = "default",
            task = null,
            dmg = 10,
            wisdom = 10,
            prospecting = 10,
            criticalStrike = 10,
            speed = 10,
            haste = 10,
            xp = 0,
            maxXp = 100,
            taskType = null,
            taskTotal = 0,
            taskProgress = 0,
            miningXp = 0,
            miningMaxXp = 100,
            miningLevel = 1,
            woodcuttingXp = 0,
            woodcuttingMaxXp = 100,
            woodcuttingLevel = 1,
            fishingXp = 0,
            fishingMaxXp = 100,
            fishingLevel = 1,
            weaponcraftingXp = 0,
            weaponcraftingMaxXp = 100,
            weaponcraftingLevel = 1,
            gearcraftingXp = 0,
            gearcraftingMaxXp = 100,
            gearcraftingLevel = 1,
            jewelrycraftingXp = 0,
            jewelrycraftingMaxXp = 100,
            jewelrycraftingLevel = 1,
            cookingXp = 0,
            cookingMaxXp = 100,
            cookingLevel = 1,
            alchemyXp = 0,
            alchemyMaxXp = 100,
            alchemyLevel = 1,
            inventoryMaxItems = 20,
            attackFire = 0,
            attackEarth = 0,
            attackWater = 0,
            attackAir = 0,
            dmgFire = 0,
            dmgEarth = 0,
            dmgWater = 0,
            dmgAir = 0,
            resFire = 0,
            resEarth = 0,
            resWater = 0,
            resAir = 0,
            weaponSlot = null,
            runeSlot = null,
            shieldSlot = null,
            helmetSlot = null,
            bodyArmorSlot = null,
            legArmorSlot = null,
            bootsSlot = null,
            ring1Slot = null,
            ring2Slot = null,
            amuletSlot = null,
            artifact1Slot = null,
            artifact2Slot = null,
            artifact3Slot = null,
            utility1Slot = null,
            utility1SlotQuantity = 0,
            utility2Slot = null,
            utility2SlotQuantity = 0,
            bagSlot = null,
            cooldownExpiration = null
        )
    }

    private fun createTestItemDocument(code: String, name: String): ItemDocument {
        return ItemDocument(
            code = code,
            name = name,
            description = "Test description",
            type = "Test type",
            subtype = "Test subtype",
            level = 1,
            tradeable = true,
            effects = null,
            craft = null,
            conditions = null
        )
    }

    private fun createTestBankItemDocument(code: String, name: String, quantity: Int): BankItemDocument {
        return BankItemDocument(
            code = code,
            name = name,
            description = "Test description",
            type = "Test type",
            subtype = "Test subtype",
            level = 1,
            tradeable = true,
            effects = null,
            craft = null,
            conditions = null,
            quantity = quantity
        )
    }
}
