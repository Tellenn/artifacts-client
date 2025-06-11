package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.ItemClient
import com.tellenn.artifacts.clients.models.ItemDetails
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.DataPage
import com.tellenn.artifacts.config.MongoTestConfiguration
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Import(MongoTestConfiguration::class, ItemSyncServiceTest.TestConfig::class)
@Testcontainers
class ItemSyncServiceTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun mockItemClient(): ItemClient = Mockito.mock(ItemClient::class.java)
    }

    @Autowired
    private lateinit var itemSyncService: ItemSyncService

    @Autowired
    private lateinit var itemRepository: ItemRepository

    @Autowired
    private lateinit var itemClient: ItemClient

    private val testItems = listOf(
        ItemDetails(
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
        ItemDetails(
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
        ItemDetails(
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
        // Clear the repository before each test
        itemRepository.deleteAll()
    }

    @AfterEach
    fun cleanup() {
        // Clean up after each test
        itemRepository.deleteAll()

        // Reset mock to avoid interference between tests
        Mockito.reset(itemClient)
    }

    @Test
    fun `should sync all items from single page`() {
        // Given
        val dataPage = DataPage(
            items = testItems,
            total = 3,
            page = 1,
            size = 50,
            pages = 1
        )
        val response = ArtifactsResponseBody(dataPage)

        `when`(itemClient.getItems(page = 1, size = 50)).thenReturn(response)

        // When
        val itemCount = itemSyncService.syncAllItems()

        // Then
        assertEquals(3, itemCount)
        assertEquals(3, itemRepository.count())

        // Verify all items were saved correctly
        val savedItems = itemRepository.findAll()
        assertTrue(savedItems.any { it.code == "TEST_SWORD_1" })
        assertTrue(savedItems.any { it.code == "TEST_POTION_1" })
        assertTrue(savedItems.any { it.code == "TEST_ARMOR_1" })

        // Verify item details were preserved
        val sword = itemRepository.findById("TEST_SWORD_1").get()
        assertEquals("Test Sword", sword.name)
        assertEquals("weapon", sword.type)
        assertEquals("common", sword.rarity)
        assertEquals(1, sword.level)
        assertTrue(sword.equippable)
    }

    @Test
    fun `should sync all items from multiple pages`() {
        // Given
        val page1Items = testItems.subList(0, 2) // First 2 items
        val page2Items = testItems.subList(2, 3) // Last item

        val dataPage1 = DataPage(
            items = page1Items,
            total = 3,
            page = 1,
            size = 2,
            pages = 2
        )
        val dataPage2 = DataPage(
            items = page2Items,
            total = 3,
            page = 2,
            size = 2,
            pages = 2
        )

        val response1 = ArtifactsResponseBody(dataPage1)
        val response2 = ArtifactsResponseBody(dataPage2)

        `when`(itemClient.getItems(page = 1, size = 50)).thenReturn(response1)
        `when`(itemClient.getItems(page = 2, size = 50)).thenReturn(response2)

        // When
        val itemCount = itemSyncService.syncAllItems()

        // Then
        assertEquals(3, itemCount)
        assertEquals(3, itemRepository.count())

        // Verify all items from both pages were saved
        val savedItems = itemRepository.findAll()
        assertTrue(savedItems.any { it.code == "TEST_SWORD_1" })
        assertTrue(savedItems.any { it.code == "TEST_POTION_1" })
        assertTrue(savedItems.any { it.code == "TEST_ARMOR_1" })
    }

    @Test
    fun `should handle empty response`() {
        // Given
        val dataPage = DataPage<ItemDetails>(
            items = emptyList(),
            total = 0,
            page = 1,
            size = 50,
            pages = 0
        )
        val response = ArtifactsResponseBody(dataPage)

        `when`(itemClient.getItems(page = 1, size = 50)).thenReturn(response)

        // When
        val itemCount = itemSyncService.syncAllItems()

        // Then
        assertEquals(0, itemCount)
        assertEquals(0, itemRepository.count())
    }

    @Test
    fun `should handle API error`() {
        // Given
        `when`(itemClient.getItems(page = 1, size = 50)).thenThrow(RuntimeException("API Error"))

        // When
        val itemCount = itemSyncService.syncAllItems()

        // Then
        assertEquals(0, itemCount)
        assertEquals(0, itemRepository.count())
    }
}
