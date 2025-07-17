package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.config.MongoTestConfiguration
import com.tellenn.artifacts.db.documents.ResourceDocument
import com.tellenn.artifacts.db.documents.ResourceDropDocument
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Import(MongoTestConfiguration::class)
@Testcontainers
class ResourceRepositoryTest {

    @Autowired
    private lateinit var resourceRepository: ResourceRepository

    @BeforeEach
    fun setup() {
        // Clear the repository and insert test data
        resourceRepository.deleteAll()
        resourceRepository.saveAll(testResources)
    }

    @AfterEach
    fun cleanup() {
        // Clean up after each test
        resourceRepository.deleteAll()
    }

    /**
     * Test for findBySkillAndLevelLessThanEqual function
     * This test verifies that the function correctly returns resources with the specified skill
     * and level less than or equal to the specified level.
     */
    @Test
    fun `should find resources by skill and level less than or equal to specified level`() {
        // Test case 1: Mining resources with level <= 10
        val miningResourcesLevel10 = resourceRepository.findBySkillAndLevelLessThanEqual("mining", 10)
        
        // Verify correct number of resources returned
        assertEquals(3, miningResourcesLevel10.size)
        
        // Verify correct resources are included
        assertTrue(miningResourcesLevel10.any { it.code == "copper_rocks" })
        assertTrue(miningResourcesLevel10.any { it.code == "tin_rocks" })
        assertTrue(miningResourcesLevel10.any { it.code == "iron_rocks" })
        
        // Verify higher level resources are excluded
        assertFalse(miningResourcesLevel10.any { it.code == "silver_rocks" })
        assertFalse(miningResourcesLevel10.any { it.code == "coal_rocks" })
        
        // Test case 2: Woodcutting resources with level <= 20
        val woodcuttingResourcesLevel20 = resourceRepository.findBySkillAndLevelLessThanEqual("woodcutting", 20)
        
        // Verify correct number of resources returned
        assertEquals(3, woodcuttingResourcesLevel20.size)
        
        // Verify correct resources are included
        assertTrue(woodcuttingResourcesLevel20.any { it.code == "ash_tree" })
        assertTrue(woodcuttingResourcesLevel20.any { it.code == "spruce_tree" })
        assertTrue(woodcuttingResourcesLevel20.any { it.code == "birch_tree" })
        
        // Verify higher level resources are excluded
        assertFalse(woodcuttingResourcesLevel20.any { it.code == "dead_tree" })
        
        // Test case 3: Fishing resources with level <= 0 (should return empty list)
        val fishingResourcesLevel0 = resourceRepository.findBySkillAndLevelLessThanEqual("fishing", 0)
        
        // Verify empty list is returned
        assertEquals(0, fishingResourcesLevel0.size)
        
        // Test case 4: Unknown skill (should return empty list)
        val unknownSkillResources = resourceRepository.findBySkillAndLevelLessThanEqual("unknown", 50)
        
        // Verify empty list is returned
        assertEquals(0, unknownSkillResources.size)
        
        // Test case 5: All mining resources (level <= 50)
        val allMiningResources = resourceRepository.findBySkillAndLevelLessThanEqual("mining", 50)
        
        // Verify all mining resources are returned
        assertEquals(5, allMiningResources.size)
    }

    // Helper method to create a ResourceDocument
    private fun createResourceDocument(code: String, name: String, skill: String, level: Int): ResourceDocument {
        val drops = listOf(
            ResourceDropDocument(
                code = "${code}_drop",
                rate = 1,
                minQuantity = 1,
                maxQuantity = 1
            )
        )

        return ResourceDocument(
            code = code,
            name = name,
            skill = skill,
            level = level,
            drops = drops
        )
    }

    // Test resources for different skills and levels
    private val testResources = listOf(
        // Mining resources
        createResourceDocument("copper_rocks", "Copper Rocks", "mining", 1),
        createResourceDocument("tin_rocks", "Tin Rocks", "mining", 5),
        createResourceDocument("iron_rocks", "Iron Rocks", "mining", 10),
        createResourceDocument("silver_rocks", "Silver Rocks", "mining", 20),
        createResourceDocument("coal_rocks", "Coal Rocks", "mining", 30),
        
        // Woodcutting resources
        createResourceDocument("ash_tree", "Ash Tree", "woodcutting", 1),
        createResourceDocument("spruce_tree", "Spruce Tree", "woodcutting", 10),
        createResourceDocument("birch_tree", "Birch Tree", "woodcutting", 20),
        createResourceDocument("dead_tree", "Dead Tree", "woodcutting", 30),
        
        // Fishing resources
        createResourceDocument("gudgeon_spot", "Gudgeon Fishing Spot", "fishing", 1),
        createResourceDocument("shrimp_spot", "Shrimp Fishing Spot", "fishing", 10),
        createResourceDocument("trout_spot", "Trout Fishing Spot", "fishing", 20)
    )
}