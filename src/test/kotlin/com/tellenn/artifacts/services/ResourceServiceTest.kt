package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.MapContent
import com.tellenn.artifacts.clients.models.MapData
import com.tellenn.artifacts.clients.models.Resource
import com.tellenn.artifacts.clients.models.ResourceDrop
import com.tellenn.artifacts.config.MongoTestConfiguration
import com.tellenn.artifacts.db.clients.MapMongoClient
import com.tellenn.artifacts.db.documents.ResourceDocument
import com.tellenn.artifacts.db.documents.ResourceDropDocument
import com.tellenn.artifacts.db.repositories.ResourceRepository
import com.tellenn.artifacts.exceptions.UnknownMapException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Import(MongoTestConfiguration::class)
@Testcontainers
class ResourceServiceTest {

    private lateinit var resourceService: ResourceService
    private lateinit var mapProximityService: MapProximityService
    private lateinit var mapMongoClient: MapMongoClient
    private lateinit var character: ArtifactsCharacter

    @Autowired
    private lateinit var resourceRepository: ResourceRepository

    @BeforeEach
    fun setUp() {
        // Clear repository
        resourceRepository.deleteAll()

        // Create mocks for services not being tested
        mapProximityService = mock(MapProximityService::class.java)
        mapMongoClient = mock(MapMongoClient::class.java)

        // Create the service with real repository and mocked services
        resourceService = ResourceService(mapProximityService, mapMongoClient, resourceRepository)

        // Create a mock character with mining level 15
        character = mock(ArtifactsCharacter::class.java)
        `when`(character.name).thenReturn("TestMiner")
        `when`(character.getLevelOf("mining")).thenReturn(15)
        `when`(character.x).thenReturn(100)
        `when`(character.y).thenReturn(100)

        // Initialize repository with test data
        initializeTestData()
    }

    @AfterEach
    fun cleanup() {
        // Clean up after each test
        resourceRepository.deleteAll()
    }

    private fun initializeTestData() {
        // Create test resources for different skills and levels
        val miningResources = listOf(
            createResourceDocument("copper_rocks", "Copper Rocks", "mining", 1),
            createResourceDocument("tin", "Tin Rocks", "mining", 5),
            createResourceDocument("iron_rocks", "Iron Rocks", "mining", 10),
            createResourceDocument("silver", "Silver Rocks", "mining", 15),
            createResourceDocument("coal_rocks", "Coal Rocks", "mining", 20),
            createResourceDocument("gold_rocks", "Gold Rocks", "mining", 30),
            createResourceDocument("strange_rocks", "Strange Rocks", "mining", 35),
            createResourceDocument("mithril_rocks", "Mithril Rocks", "mining", 40)
        )

        val woodcuttingResources = listOf(
            createResourceDocument("ash_tree", "Ash Tree", "woodcutting", 1),
            createResourceDocument("spruce_tree", "Spruce Tree", "woodcutting", 10),
            createResourceDocument("birch_tree", "Birch Tree", "woodcutting", 20),
            createResourceDocument("dead_tree", "Dead Tree", "woodcutting", 30),
            createResourceDocument("magic_tree", "Magic Tree", "woodcutting", 35),
            createResourceDocument("maple_tree", "Maple Tree", "woodcutting", 40)
        )

        val fishingResources = listOf(
            createResourceDocument("gudgeon_fishing_spot", "Gudgeon Fishing Spot", "fishing", 1),
            createResourceDocument("shrimp_fishing_spot", "Shrimp Fishing Spot", "fishing", 10),
            createResourceDocument("trout_fishing_spot", "Trout Fishing Spot", "fishing", 20),
            createResourceDocument("bass_fishing_spot", "Bass Fishing Spot", "fishing", 30),
            createResourceDocument("salmon_fishing_spot", "Salmon Fishing Spot", "fishing", 40)
        )

        val alchemyResources = listOf(
            createResourceDocument("sunflower_field", "Sunflower Field", "alchemy", 1),
            createResourceDocument("nettle", "Nettle", "alchemy", 20),
            createResourceDocument("glowstem", "Glowstem", "alchemy", 40)
        )

        // Save all resources to the repository
        resourceRepository.saveAll(miningResources + woodcuttingResources + fishingResources + alchemyResources)
    }

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

    @Test
    fun `findClosestMapWithResource should return closest map with highest level resource`() {
        // Arrange
        val silverMap = MapData(
            name = "Silver Mine",
            skin = "mine",
            x = 150,
            y = 150,
            content = MapContent(type = "ore", code = "silver")
        )

        // Mock the mapProximityService to return the silver map
        `when`(mapProximityService.findClosestMap(character, "resource", "silver"))
            .thenReturn(silverMap)

        // Act
        val result = resourceService.findClosestMapWithResource(character, "mining")

        // Assert
        assertEquals(silverMap, result)
        verify(mapProximityService).findClosestMap(character, "resource", "silver")
    }

    @Test
    fun `findClosestMapWithResource should fall back to lower level resource if higher level not found`() {
        // Arrange
        val ironMap = MapData(
            name = "Iron Mine",
            skin = "mine",
            x = 150,
            y = 150,
            content = MapContent(type = "ore", code = "iron")
        )

        // Mock the mapProximityService to throw exception for silver but return iron map
        `when`(mapProximityService.findClosestMap(character, "resource", "silver"))
            .thenThrow(UnknownMapException("ore", "silver"))
        `when`(mapProximityService.findClosestMap(character, "resource", "iron_rocks"))
            .thenReturn(ironMap)

        // Act
        val result = resourceService.findClosestMapWithResource(character, "mining")

        // Assert
        assertEquals(ironMap, result)
        verify(mapProximityService).findClosestMap(character, "resource", "silver")
        verify(mapProximityService).findClosestMap(character, "resource", "iron_rocks")
    }

    @Test
    fun `findClosestMapWithResource should throw exception if no map found for any resource`() {
        // Arrange
        // Mock the mapProximityService to throw exception for all resources
        // Set up mocks for all possible resource codes
        `when`(mapProximityService.findClosestMap(character, "resource", "silver"))
            .thenThrow(UnknownMapException("ore", "silver"))
        `when`(mapProximityService.findClosestMap(character, "resource", "iron_rocks"))
            .thenThrow(UnknownMapException("ore", "iron_rocks"))
        `when`(mapProximityService.findClosestMap(character, "resource", "tin"))
            .thenThrow(UnknownMapException("ore", "tin"))
        `when`(mapProximityService.findClosestMap(character, "resource", "copper_rocks"))
            .thenThrow(UnknownMapException("ore", "copper_rocks"))

        // Act & Assert
        assertThrows<UnknownMapException> {
            resourceService.findClosestMapWithResource(character, "mining")
        }
    }

    @Test
    fun `findClosestMapWithResource should throw exception for unknown resource type`() {
        // Act & Assert
        assertThrows<IllegalArgumentException> {
            resourceService.findClosestMapWithResource(character, "unknown")
        }
    }

    @Test
    fun `findClosestMapWithResource should filter resources by character level`() {
        // Arrange
        val lowLevelCharacter = mock(ArtifactsCharacter::class.java)
        `when`(lowLevelCharacter.name).thenReturn("LowLevelMiner")
        `when`(lowLevelCharacter.getLevelOf("mining")).thenReturn(7) // Can only mine copper and tin
        `when`(lowLevelCharacter.x).thenReturn(100)
        `when`(lowLevelCharacter.y).thenReturn(100)

        val tinMap = MapData(
            name = "Tin Mine",
            skin = "mine",
            x = 150,
            y = 150,
            content = MapContent(type = "mining", code = "tin")
        )

        // Mock the mapProximityService to return the tin map
        `when`(mapProximityService.findClosestMap(lowLevelCharacter, "resource", "tin"))
            .thenReturn(tinMap)

        // Act
        val result = resourceService.findClosestMapWithResource(lowLevelCharacter, "mining")

        // Assert
        assertEquals(tinMap, result)
        verify(mapProximityService).findClosestMap(lowLevelCharacter, "resource", "tin")
        // Verify that it didn't try to find iron or higher level resources
        verify(mapProximityService, never()).findClosestMap(lowLevelCharacter, "resource", "iron_rocks")
        verify(mapProximityService, never()).findClosestMap(lowLevelCharacter, "resource", "silver")
    }

    @Test
    fun `getResourcesBySkill should return all resources for a skill`() {
        // Act
        val result = resourceService.getResourcesBySkill("mining")

        // Assert
        assertEquals(8, result.size)
        assertEquals("copper_rocks", result[0].code)
        assertEquals("tin", result[1].code)
        assertEquals("iron_rocks", result[2].code)
        assertEquals("silver", result[3].code)
        assertEquals("coal_rocks", result[4].code)
        assertEquals("gold_rocks", result[5].code)
        assertEquals("strange_rocks", result[6].code)
        assertEquals("mithril_rocks", result[7].code)
    }

    @Test
    fun `getResourcesBySkill should return empty list for unknown skill`() {
        // Act
        val result = resourceService.getResourcesBySkill("unknown")

        // Assert
        assertEquals(0, result.size)
    }

    @Test
    fun `getResourcesBySkillAndMaxLevel should return resources up to max level`() {
        // Act
        val result = resourceService.getResourcesBySkillAndMaxLevel("mining", 20)

        // Assert
        assertEquals(5, result.size)
        assertEquals("copper_rocks", result[0].code)
        assertEquals("tin", result[1].code)
        assertEquals("iron_rocks", result[2].code)
        assertEquals("silver", result[3].code)
        assertEquals("coal_rocks", result[4].code)
    }

    @Test
    fun `getResourcesBySkillAndMaxLevel should return empty list for unknown skill`() {
        // Act
        val result = resourceService.getResourcesBySkillAndMaxLevel("unknown", 20)

        // Assert
        assertEquals(0, result.size)
    }

    @Test
    fun `getResourcesBySkillAndMaxLevel should return empty list for level below minimum`() {
        // Act
        val result = resourceService.getResourcesBySkillAndMaxLevel("mining", 0)

        // Assert
        assertEquals(0, result.size)
    }
}
