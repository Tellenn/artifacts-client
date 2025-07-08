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
class ItemMongoClientTest {

    @Autowired
    private lateinit var itemMongoClient: ItemMongoClient

    @Autowired
    private lateinit var itemRepository: ItemRepository

    private val testItems = listOf(
        ItemDocument(
            code = "TEST_SWORD_1",
            name = "Test Sword",
            description = "A test sword for testing",
            type = "weapon",
            subtype = "sword",
            level = 1,
            tradeable = true,
            effects = null,
            craft = null
        ),
        ItemDocument(
            code = "TEST_POTION_1",
            name = "Test Potion",
            description = "A test potion for testing",
            type = "consumable",
            subtype = "potion",
            level = 5,
            tradeable = true,
            effects = null,
            craft = null
        ),
        ItemDocument(
            code = "TEST_ARMOR_1",
            name = "Test Armor",
            description = "A test armor for testing",
            type = "armor",
            subtype = "body",
            level = 10,
            tradeable = false,
            effects = null,
            craft = null
        ),
        ItemDocument(
            code = "TEST_SHIELD_1",
            name = "Test Shield",
            description = "A test shield for testing",
            type = "armor",
            subtype = "shield",
            level = 15,
            tradeable = true,
            effects = null,
            craft = null
        ),
        ItemDocument(
            code = "TEST_HELMET_1",
            name = "Test Helmet",
            description = "A test helmet for testing",
            type = "armor",
            subtype = "head",
            level = 20,
            tradeable = false,
            effects = null,
            craft = null
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
        val response = itemMongoClient.getItems()

        // Then
        assertEquals(5, response.total)
        assertEquals(5, response.data.size)
        assertTrue(response.data.any { it.code == "TEST_SWORD_1" })
        assertTrue(response.data.any { it.code == "TEST_POTION_1" })
        assertTrue(response.data.any { it.code == "TEST_ARMOR_1" })
        assertTrue(response.data.any { it.code == "TEST_SHIELD_1" })
        assertTrue(response.data.any { it.code == "TEST_HELMET_1" })
    }

    @Test
    fun `should get items with pagination`() {
        // When
        val response = itemMongoClient.getItems(page = 1, size = 2)

        // Then
        assertEquals(5, response.total)
        assertEquals(2, response.data.size)
        assertEquals(3, response.pages)
        assertEquals(1, response.page)
    }

    @Test
    fun `should get items by name`() {
        // When
        val response = itemMongoClient.getItems(name = "sword")

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_SWORD_1", response.data[0].code)
        assertEquals("Test Sword", response.data[0].name)
    }

    @Test
    fun `should get items by type`() {
        // When
        val response = itemMongoClient.getItems(type = "armor")

        // Then
        assertEquals(3, response.total)
        assertTrue(response.data.any { it.code == "TEST_ARMOR_1" })
        assertTrue(response.data.any { it.code == "TEST_SHIELD_1" })
        assertTrue(response.data.any { it.code == "TEST_HELMET_1" })
    }

    @Test
    fun `should get items by subtype`() {
        // When
        val response = itemMongoClient.getItems(subtype = "shield")

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_SHIELD_1", response.data[0].code)
    }

    @Test
    fun `should get items by level`() {
        // When
        val response = itemMongoClient.getItems(level = 5)

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_POTION_1", response.data[0].code)
    }

    @Test
    fun `should get items by level range`() {
        // When
        val response = itemMongoClient.getItems(minLevel = 10, maxLevel = 15)

        // Then
        assertEquals(2, response.total)
        assertTrue(response.data.any { it.code == "TEST_ARMOR_1" })
        assertTrue(response.data.any { it.code == "TEST_SHIELD_1" })
    }

    @Test
    fun `should get items by minimum level`() {
        // When
        val response = itemMongoClient.getItems(minLevel = 15)

        // Then
        assertEquals(2, response.total)
        assertTrue(response.data.any { it.code == "TEST_SHIELD_1" })
        assertTrue(response.data.any { it.code == "TEST_HELMET_1" })
    }

    @Test
    fun `should get items by maximum level`() {
        // When
        val response = itemMongoClient.getItems(maxLevel = 5)

        // Then
        assertEquals(2, response.total)
        assertTrue(response.data.any { it.code == "TEST_SWORD_1" })
        assertTrue(response.data.any { it.code == "TEST_POTION_1" })
    }

    @Test
    fun `should get items by tradeable`() {
        // When
        val response = itemMongoClient.getItems(tradeable = true)

        // Then
        assertEquals(3, response.total)
        assertTrue(response.data.any { it.code == "TEST_SWORD_1" })
        assertTrue(response.data.any { it.code == "TEST_POTION_1" })
        assertTrue(response.data.any { it.code == "TEST_SHIELD_1" })
    }

    @Test
    fun `should get items by multiple criteria`() {
        // When
        val response = itemMongoClient.getItems(type = "armor", minLevel = 15, tradeable = true)

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_SHIELD_1", response.data[0].code)
    }

    @Test
    fun `should get item details by code`() {
        // When
        val response = itemMongoClient.getItemDetails("TEST_SWORD_1")

        // Then
        assertEquals("TEST_SWORD_1", response.data.code)
        assertEquals("Test Sword", response.data.name)
        assertEquals("A test sword for testing", response.data.description)
        assertEquals("weapon", response.data.type)
        assertEquals("sword", response.data.subtype)
        assertEquals(1, response.data.level)
        assertTrue(response.data.tradeable)
        assertNull(response.data.effects)
        assertNull(response.data.craft)
    }

    @Test
    fun `should throw exception when item not found`() {
        // Then
        assertThrows(NoSuchElementException::class.java) {
            // When
            itemMongoClient.getItemDetails("NONEXISTENT_ITEM")
        }
    }
}