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
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.Optional

class DatabaseClientTest {

    private lateinit var databaseClient: DatabaseClient
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

    private fun <T> any(type: Class<T>): T = Mockito.any(type)
    private fun <T> eq(value: T): T = Mockito.eq(value)

    @BeforeEach
    fun setup() {
        itemRepository = mock(ItemRepository::class.java)
        databaseClient = DatabaseClient(itemRepository)

        // Default mock behaviors
        `when`(itemRepository.findAll(any(Pageable::class.java))).thenReturn(PageImpl(testItems))
    }

    @AfterEach
    fun cleanup() {
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
        // Given
        val subList = testItems.subList(0, 2)
        `when`(itemRepository.findAll(any(Pageable::class.java))).thenReturn(PageImpl(subList, PageRequest.of(0, 2), 3))

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
        // Given
        val filtered = testItems.filter { it.name.contains("sword", ignoreCase = true) }
        `when`(itemRepository.findByNameContainingIgnoreCase(eq("sword"), any(Pageable::class.java))).thenReturn(PageImpl(filtered))

        // When
        val response = databaseClient.getItems(name = "sword")

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_SWORD_1", response.data[0].code)
        assertEquals("Test Sword", response.data[0].name)
    }

    @Test
    fun `should get items by type`() {
        // Given
        val filtered = testItems.filter { it.type == "weapon" }
        `when`(itemRepository.findByType(eq("weapon"), any(Pageable::class.java))).thenReturn(PageImpl(filtered))

        // When
        val response = databaseClient.getItems(type = "weapon")

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_SWORD_1", response.data[0].code)
    }

    @Test
    fun `should get items by subtype`() {
        // Given
        val filtered = testItems.filter { it.subtype == "body" }
        `when`(itemRepository.findBySubtype(eq("body"), any(Pageable::class.java))).thenReturn(PageImpl(filtered))

        // When
        val response = databaseClient.getItems(subtype = "body")

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_ARMOR_1", response.data[0].code)
    }

    @Test
    fun `should get items by level`() {
        // Given
        val filtered = testItems.filter { it.level == 5 }
        `when`(itemRepository.findByLevel(eq(5), any(Pageable::class.java))).thenReturn(PageImpl(filtered))

        // When
        val response = databaseClient.getItems(level = 5)

        // Then
        assertEquals(1, response.total)
        assertEquals("TEST_POTION_1", response.data[0].code)
    }

    @Test
    fun `should get items by tradable`() {
        // Given
        val filtered = testItems.filter { it.tradeable == true }
        `when`(itemRepository.findByTradeable(eq(true), any(Pageable::class.java))).thenReturn(PageImpl(filtered))

        // When
        val response = databaseClient.getItems(tradeable = true)

        // Then
        assertEquals(2, response.total)
        assertTrue(response.data.any { it.code == "TEST_SWORD_1" })
        assertTrue(response.data.any { it.code == "TEST_POTION_1" })
    }

    @Test
    fun `should get item details by code`() {
        // Given
        `when`(itemRepository.findById("TEST_SWORD_1")).thenReturn(Optional.of(testItems[0]))

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
        // Given
        `when`(itemRepository.findById("NONEXISTENT_ITEM")).thenReturn(Optional.empty())

        // Then
        assertThrows(NoSuchElementException::class.java) {
            // When
            databaseClient.getItemDetails("NONEXISTENT_ITEM")
        }
    }
}
