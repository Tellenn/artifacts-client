package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.config.MongoTestConfiguration
import com.tellenn.artifacts.db.documents.ItemDocument
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Import(MongoTestConfiguration::class)
@Testcontainers
class ItemRepositoryTest {

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
    fun `should find all items`() {
        // When
        val items = itemRepository.findAll()

        // Then
        assertEquals(3, items.size)
        assertTrue(items.any { it.code == "TEST_SWORD_1" })
        assertTrue(items.any { it.code == "TEST_POTION_1" })
        assertTrue(items.any { it.code == "TEST_ARMOR_1" })
    }

    @Test
    fun `should find item by id`() {
        // When
        val item = itemRepository.findById("TEST_SWORD_1")

        // Then
        assertTrue(item.isPresent)
        assertEquals("Test Sword", item.get().name)
        assertEquals("weapon", item.get().type)
    }

    @Test
    fun `should find items by name containing`() {
        // When
        val pageable = PageRequest.of(0, 10)
        val items = itemRepository.findByNameContainingIgnoreCase("sword", pageable)

        // Then
        assertEquals(1, items.totalElements)
        assertEquals("TEST_SWORD_1", items.content[0].code)
    }

    @Test
    fun `should find items by type`() {
        // When
        val pageable = PageRequest.of(0, 10)
        val items = itemRepository.findByType("weapon", pageable)

        // Then
        assertEquals(1, items.totalElements)
        assertEquals("TEST_SWORD_1", items.content[0].code)
    }

    @Test
    fun `should find items by subtype`() {
        // When
        val pageable = PageRequest.of(0, 10)
        val items = itemRepository.findBySubtype("body", pageable)

        // Then
        assertEquals(1, items.totalElements)
        assertEquals("TEST_ARMOR_1", items.content[0].code)
    }

    @Test
    fun `should find items by level`() {
        // When
        val pageable = PageRequest.of(0, 10)
        val items = itemRepository.findByLevel(5, pageable)

        // Then
        assertEquals(1, items.totalElements)
        assertEquals("TEST_POTION_1", items.content[0].code)
    }

    @Test
    fun `should find items by tradable`() {
        // When
        val pageable = PageRequest.of(0, 10)
        val items = itemRepository.findByTradeable(true, pageable)

        // Then
        assertEquals(2, items.totalElements)
        assertTrue(items.content.any { it.code == "TEST_SWORD_1" })
        assertTrue(items.content.any { it.code == "TEST_POTION_1" })
    }
}
