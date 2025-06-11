package com.tellenn.artifacts.db.clients

import com.tellenn.artifacts.config.MongoTestConfiguration
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Import(MongoTestConfiguration::class)
@Testcontainers
class DatabaseClientTest {

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var itemRepository: ItemRepository

    private val testItems = listOf(
        ItemDocument(
            code = "TEST_SWORD_1",
            name = "Test Sword",
            description = "A test sword for testing",
            type = "weapon",
            rarity = "common",
            level = 1,
            value = 100,
            weight = 5,
            stackable = false,
            usable = false,
            equippable = true,
            slot = "hand",
            stats = null,
            recipe = null
        ),
        ItemDocument(
            code = "TEST_POTION_1",
            name = "Test Potion",
            description = "A test potion for testing",
            type = "consumable",
            rarity = "uncommon",
            level = 5,
            value = 50,
            weight = 1,
            stackable = true,
            usable = true,
            equippable = false,
            slot = null,
            stats = null,
            recipe = null
        ),
        ItemDocument(
            code = "TEST_ARMOR_1",
            name = "Test Armor",
            description = "A test armor for testing",
            type = "armor",
            rarity = "rare",
            level = 10,
            value = 500,
            weight = 20,
            stackable = false,
            usable = false,
            equippable = true,
            slot = "body",
            stats = null,
            recipe = null
        )
    )

    @BeforeEach
    fun setup() {
        // Clear the repository and insert test data
        itemRepository.deleteAll()
        itemRepository.saveAll(testItems)
    }

    @AfterEach
    fun cleanup() {
        // Clean up after each test
        itemRepository.deleteAll()
    }

    @Test
    fun `should get all items`() {
        // When
        val response = databaseClient.getItems()
        
        // Then
        assertEquals(3, response.data.total)
        assertEquals(3, response.data.items.size)
        assertTrue(response.data.items.any { it.code == "TEST_SWORD_1" })
        assertTrue(response.data.items.any { it.code == "TEST_POTION_1" })
        assertTrue(response.data.items.any { it.code == "TEST_ARMOR_1" })
    }

    @Test
    fun `should get items with pagination`() {
        // When
        val response = databaseClient.getItems(page = 1, size = 2)
        
        // Then
        assertEquals(3, response.data.total)
        assertEquals(2, response.data.items.size)
        assertEquals(2, response.data.pages)
        assertEquals(1, response.data.page)
    }

    @Test
    fun `should get items by name`() {
        // When
        val response = databaseClient.getItems(name = "sword")
        
        // Then
        assertEquals(1, response.data.total)
        assertEquals("TEST_SWORD_1", response.data.items[0].code)
        assertEquals("Test Sword", response.data.items[0].name)
    }

    @Test
    fun `should get items by type`() {
        // When
        val response = databaseClient.getItems(type = "weapon")
        
        // Then
        assertEquals(1, response.data.total)
        assertEquals("TEST_SWORD_1", response.data.items[0].code)
    }

    @Test
    fun `should get items by rarity`() {
        // When
        val response = databaseClient.getItems(rarity = "rare")
        
        // Then
        assertEquals(1, response.data.total)
        assertEquals("TEST_ARMOR_1", response.data.items[0].code)
    }

    @Test
    fun `should get items by level`() {
        // When
        val response = databaseClient.getItems(level = 5)
        
        // Then
        assertEquals(1, response.data.total)
        assertEquals("TEST_POTION_1", response.data.items[0].code)
    }

    @Test
    fun `should get items by equippable`() {
        // When
        val response = databaseClient.getItems(equippable = true)
        
        // Then
        assertEquals(2, response.data.total)
        assertTrue(response.data.items.any { it.code == "TEST_SWORD_1" })
        assertTrue(response.data.items.any { it.code == "TEST_ARMOR_1" })
    }

    @Test
    fun `should get items by usable`() {
        // When
        val response = databaseClient.getItems(usable = true)
        
        // Then
        assertEquals(1, response.data.total)
        assertEquals("TEST_POTION_1", response.data.items[0].code)
    }

    @Test
    fun `should get items by stackable`() {
        // When
        val response = databaseClient.getItems(stackable = true)
        
        // Then
        assertEquals(1, response.data.total)
        assertEquals("TEST_POTION_1", response.data.items[0].code)
    }

    @Test
    fun `should get items by slot`() {
        // When
        val response = databaseClient.getItems(slot = "body")
        
        // Then
        assertEquals(1, response.data.total)
        assertEquals("TEST_ARMOR_1", response.data.items[0].code)
    }

    @Test
    fun `should get item details by code`() {
        // When
        val response = databaseClient.getItemDetails("TEST_SWORD_1")
        
        // Then
        assertEquals("TEST_SWORD_1", response.data.code)
        assertEquals("Test Sword", response.data.name)
        assertEquals("A test sword for testing", response.data.description)
        assertEquals("weapon", response.data.type)
        assertEquals("common", response.data.rarity)
        assertEquals(1, response.data.level)
        assertEquals(100, response.data.value)
        assertEquals(5, response.data.weight)
        assertFalse(response.data.stackable)
        assertFalse(response.data.usable)
        assertTrue(response.data.equippable)
        assertEquals("hand", response.data.slot)
    }

    @Test
    fun `should throw exception when item not found`() {
        // Then
        assertThrows(NoSuchElementException::class.java) {
            // When
            databaseClient.getItemDetails("NONEXISTENT_ITEM")
        }
    }
}