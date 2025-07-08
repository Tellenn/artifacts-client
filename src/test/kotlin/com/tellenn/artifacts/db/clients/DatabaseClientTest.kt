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
import org.springframework.data.mongodb.core.MongoTemplate

@SpringBootTest
@Import(MongoTestConfiguration::class)
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
        )
    )



    @BeforeEach
    fun setup() {

        // Clear the repository and insert test data`
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
        assertEquals(3, response.total)
        assertEquals(3, response.data.size)
        assertTrue(response.data.any { it.code == "TEST_SWORD_1" })
        assertTrue(response.data.any { it.code == "TEST_POTION_1" })
        assertTrue(response.data.any { it.code == "TEST_ARMOR_1" })
    }

    @Test
    fun `should get items with pagination`() {
        // When
        val response = databaseClient.getItems(page = 1, size = 2)

        // Then
        assertEquals(3, response.total)
        assertEquals(2, response.data.size)
        assertEquals(2, response.pages)
        assertEquals(1, response.page)
    }

    @Test
    fun `should get items by name`() {
        // When
        val response = databaseClient.getItems(name = "sword")

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_SWORD_1", response.data[0].code)
        assertEquals("Test Sword", response.data[0].name)
    }

    @Test
    fun `should get items by type`() {
        // When
        val response = databaseClient.getItems(type = "weapon")

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_SWORD_1", response.data[0].code)
    }

    @Test
    fun `should get items by subtype`() {
        // When
        val response = databaseClient.getItems(subtype = "body")

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_ARMOR_1", response.data[0].code)
    }

    @Test
    fun `should get items by level`() {
        // When
        val response = databaseClient.getItems(level = 5)

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_POTION_1", response.data[0].code)
    }

    @Test
    fun `should get items by tradable`() {
        // When
        val response = databaseClient.getItems(tradeable = true)

        // Then
        assertEquals(2, response.total)
        assertTrue(response.data.any { it.code == "TEST_SWORD_1" })
        assertTrue(response.data.any { it.code == "TEST_POTION_1" })
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
            databaseClient.getItemDetails("NONEXISTENT_ITEM")
        }
    }
}
