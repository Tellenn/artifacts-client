package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.models.ItemDetails
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.Optional

class ItemRepositoryTest {

    private lateinit var itemRepository: ItemRepository

    private val testItems = listOf(
        ItemDetails(
            code = "TEST_SWORD_1",
            name = "Test Sword",
            description = "A test sword for testing",
            type = "weapon",
            subtype = "sword",
            level = 1,
            tradeable = true,
            effects = null,
            craft = null,
            conditions = null
        ),
        ItemDetails(
            code = "TEST_POTION_1",
            name = "Test Potion",
            description = "A test potion for testing",
            type = "consumable",
            subtype = "potion",
            level = 5,
            tradeable = true,
            effects = null,
            craft = null,
            conditions = null
        ),
        ItemDetails(
            code = "TEST_ARMOR_1",
            name = "Test Armor",
            description = "A test armor for testing",
            type = "armor",
            subtype = "body",
            level = 10,
            tradeable = false,
            effects = null,
            craft = null,
            conditions = null
        )
    )

    @BeforeEach
    fun setup() {
        itemRepository = mock(ItemRepository::class.java)
    }

    @AfterEach
    fun cleanup() {
    }

    @Test
    fun `should find all items`() {
        // Given
        `when`(itemRepository.findAll()).thenReturn(testItems)

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
        // Given
        `when`(itemRepository.findById("TEST_SWORD_1")).thenReturn(Optional.of(testItems[0]))

        // When
        val item = itemRepository.findById("TEST_SWORD_1")

        // Then
        assertTrue(item.isPresent)
        assertEquals("Test Sword", item.get().name)
        assertEquals("weapon", item.get().type)
    }

    @Test
    fun `should find items by name containing`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val filtered = testItems.filter { it.name.contains("sword", ignoreCase = true) }
        `when`(itemRepository.findByNameContainingIgnoreCase("sword", pageable)).thenReturn(PageImpl(filtered))

        // When
        val items = itemRepository.findByNameContainingIgnoreCase("sword", pageable)

        // Then
        assertEquals(1, items.totalElements)
        assertEquals("TEST_SWORD_1", items.content[0].code)
    }

    @Test
    fun `should find items by type`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val filtered = testItems.filter { it.type == "weapon" }
        `when`(itemRepository.findByType("weapon", pageable)).thenReturn(PageImpl(filtered))

        // When
        val items = itemRepository.findByType("weapon", pageable)

        // Then
        assertEquals(1, items.totalElements)
        assertEquals("TEST_SWORD_1", items.content[0].code)
    }

    @Test
    fun `should find items by subtype`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val filtered = testItems.filter { it.subtype == "body" }
        `when`(itemRepository.findBySubtype("body", pageable)).thenReturn(PageImpl(filtered))

        // When
        val items = itemRepository.findBySubtype("body", pageable)

        // Then
        assertEquals(1, items.totalElements)
        assertEquals("TEST_ARMOR_1", items.content[0].code)
    }

    @Test
    fun `should find items by level`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val filtered = testItems.filter { it.level == 5 }
        `when`(itemRepository.findByLevel(5, pageable)).thenReturn(PageImpl(filtered))

        // When
        val items = itemRepository.findByLevel(5, pageable)

        // Then
        assertEquals(1, items.totalElements)
        assertEquals("TEST_POTION_1", items.content[0].code)
    }

    @Test
    fun `should find items by tradable`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val filtered = testItems.filter { it.tradeable == true }
        `when`(itemRepository.findByTradeable(true, pageable)).thenReturn(PageImpl(filtered))

        // When
        val items = itemRepository.findByTradeable(true, pageable)

        // Then
        assertEquals(2, items.totalElements)
        assertTrue(items.content.any { it.code == "TEST_SWORD_1" })
        assertTrue(items.content.any { it.code == "TEST_POTION_1" })
    }
}
