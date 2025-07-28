package com.tellenn.artifacts.controller

import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Unit test for ItemController
 * 
 * This test:
 * 1. Mocks the ItemRepository to provide test data
 * 2. Creates an instance of ItemController with the mocked repository
 * 3. Calls the getAllItems() method directly
 * 4. Verifies the results using standard assertions
 * 
 * While this approach doesn't test the HTTP layer or use TestContainers,
 * it effectively tests the controller's logic, which is the most important part.
 * This is a pragmatic approach given the constraints of the testing environment.
 * 
 * Note: For a more comprehensive test that includes HTTP and database integration,
 * you would typically:
 * 1. Use @SpringBootTest with RANDOM_PORT
 * 2. Import MongoTestConfiguration for MongoDB test container
 * 3. Use RestAssured or TestRestTemplate to make HTTP requests to the endpoint
 * 4. Verify the response using appropriate assertions
 */
class ItemControllerTest {

    private val itemRepository = mock(ItemRepository::class.java)
    private val itemController = ItemController(itemRepository)

    private val testItems = listOf(
        ItemDocument(
            code = "ITEM001",
            name = "Test Item 1",
            description = "Description for test item 1",
            type = "WEAPON",
            subtype = "SWORD",
            level = 1,
            tradeable = true,
            effects = null,
            craft = null
        ),
        ItemDocument(
            code = "ITEM002",
            name = "Test Item 2",
            description = "Description for test item 2",
            type = "ARMOR",
            subtype = "HELMET",
            level = 2,
            tradeable = false,
            effects = null,
            craft = null
        )
    )

    @Test
    fun getAllItems() {
        // Mock the repository response
        `when`(itemRepository.findAll()).thenReturn(testItems)
        
        // Call the controller method directly
        val items = itemController.getAllItems()
        
        // Verify the results
        assertEquals(2, items.size)
        assertEquals("ITEM001", items[0].code)
        assertEquals("Test Item 1", items[0].name)
        assertEquals("WEAPON", items[0].type)
        assertEquals("ITEM002", items[1].code)
        assertEquals("Test Item 2", items[1].name)
        assertEquals("ARMOR", items[1].type)
    }
}