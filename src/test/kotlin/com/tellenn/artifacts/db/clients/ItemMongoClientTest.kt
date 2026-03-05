package com.tellenn.artifacts.db.clients

import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.Optional

class ItemMongoClientTest {

    private lateinit var itemMongoClient: ItemMongoClient
    private lateinit var itemRepository: ItemRepository
    private lateinit var mongoTemplate: MongoTemplate

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

    private fun <T> any(type: Class<T>): T = Mockito.any(type)
    private fun <T> eq(value: T): T = Mockito.eq(value)

    @BeforeEach
    fun setup() {
        itemRepository = mock(ItemRepository::class.java)
        mongoTemplate = mock(MongoTemplate::class.java)
        itemMongoClient = ItemMongoClient(itemRepository, mongoTemplate)

        // Default mock behaviors
        `when`(itemRepository.findAll(any(Pageable::class.java))).thenReturn(PageImpl(testItems))
    }

    @AfterEach
    fun cleanup() {
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
        // Given
        val subList = testItems.subList(0, 2)
        `when`(itemRepository.findAll(any(Pageable::class.java))).thenReturn(PageImpl(subList, PageRequest.of(0, 2), 5))

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
        // Given
        val filtered = testItems.filter { it.name.contains("sword", ignoreCase = true) }
        `when`(itemRepository.findByNameContainingIgnoreCase(eq("sword"), any(Pageable::class.java))).thenReturn(PageImpl(filtered))

        // When
        val response = itemMongoClient.getItems(name = "sword")

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_SWORD_1", response.data[0].code)
        assertEquals("Test Sword", response.data[0].name)
    }

    @Test
    fun `should get items by type`() {
        // Given
        val filtered = testItems.filter { it.type == "armor" }
        `when`(itemRepository.findByType(eq("armor"), any(Pageable::class.java))).thenReturn(PageImpl(filtered))

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
        // Given
        val filtered = testItems.filter { it.subtype == "shield" }
        `when`(itemRepository.findBySubtype(eq("shield"), any(Pageable::class.java))).thenReturn(PageImpl(filtered))

        // When
        val response = itemMongoClient.getItems(subtype = "shield")

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_SHIELD_1", response.data[0].code)
    }

    @Test
    fun `should get items by level`() {
        // Given
        val filtered = testItems.filter { it.level == 5 }
        `when`(itemRepository.findByLevel(eq(5), any(Pageable::class.java))).thenReturn(PageImpl(filtered))

        // When
        val response = itemMongoClient.getItems(level = 5)

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_POTION_1", response.data[0].code)
    }

    @Test
    fun `should get items by level range`() {
        // Given
        val filtered = testItems.filter { it.level in 10..15 }
        `when`(mongoTemplate.find(any(Query::class.java), eq(ItemDocument::class.java))).thenReturn(filtered)
        `when`(mongoTemplate.count(any(Query::class.java), eq(ItemDocument::class.java))).thenReturn(filtered.size.toLong())

        // When
        val response = itemMongoClient.getItems(minLevel = 10, maxLevel = 15)

        // Then
        assertEquals(2, response.total)
        assertTrue(response.data.any { it.code == "TEST_ARMOR_1" })
        assertTrue(response.data.any { it.code == "TEST_SHIELD_1" })
    }

    @Test
    fun `should get items by minimum level`() {
        // Given
        val filtered = testItems.filter { it.level >= 15 }
        `when`(mongoTemplate.find(any(Query::class.java), eq(ItemDocument::class.java))).thenReturn(filtered)
        `when`(mongoTemplate.count(any(Query::class.java), eq(ItemDocument::class.java))).thenReturn(filtered.size.toLong())

        // When
        val response = itemMongoClient.getItems(minLevel = 15)

        // Then
        assertEquals(2, response.total)
        assertTrue(response.data.any { it.code == "TEST_SHIELD_1" })
        assertTrue(response.data.any { it.code == "TEST_HELMET_1" })
    }

    @Test
    fun `should get items by maximum level`() {
        // Given
        val filtered = testItems.filter { it.level <= 5 }
        `when`(mongoTemplate.find(any(Query::class.java), eq(ItemDocument::class.java))).thenReturn(filtered)
        `when`(mongoTemplate.count(any(Query::class.java), eq(ItemDocument::class.java))).thenReturn(filtered.size.toLong())

        // When
        val response = itemMongoClient.getItems(maxLevel = 5)

        // Then
        assertEquals(2, response.total)
        assertTrue(response.data.any { it.code == "TEST_SWORD_1" })
        assertTrue(response.data.any { it.code == "TEST_POTION_1" })
    }

    @Test
    fun `should get items by tradeable`() {
        // Given
        val filtered = testItems.filter { it.tradeable == true }
        `when`(itemRepository.findByTradeable(eq(true), any(Pageable::class.java))).thenReturn(PageImpl(filtered))

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
        // Given
        val filtered = testItems.filter { it.type == "armor" && it.level >= 15 && it.tradeable }
        `when`(mongoTemplate.find(any(Query::class.java), eq(ItemDocument::class.java))).thenReturn(filtered)
        `when`(mongoTemplate.count(any(Query::class.java), eq(ItemDocument::class.java))).thenReturn(filtered.size.toLong())

        // When
        val response = itemMongoClient.getItems(type = "armor", minLevel = 15, tradeable = true)

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_SHIELD_1", response.data[0].code)
    }

    @Test
    fun `should get item details by code`() {
        // Given
        `when`(itemRepository.findById("TEST_SWORD_1")).thenReturn(Optional.of(testItems[0]))

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
        // Given
        `when`(itemRepository.findById("NONEXISTENT_ITEM")).thenReturn(Optional.empty())

        // Then
        assertThrows(NoSuchElementException::class.java) {
            // When
            itemMongoClient.getItemDetails("NONEXISTENT_ITEM")
        }
    }
}
