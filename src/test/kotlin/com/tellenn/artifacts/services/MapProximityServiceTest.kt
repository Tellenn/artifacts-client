package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.MapContent
import com.tellenn.artifacts.clients.models.MapData
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.db.clients.MapMongoClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Instant

class MapProximityServiceTest {

    private lateinit var mapMongoClient: MapMongoClient
    private lateinit var mapProximityService: MapProximityService

    @BeforeEach
    fun setUp() {
        mapMongoClient = mock(MapMongoClient::class.java)
        mapProximityService = MapProximityService(mapMongoClient)
    }

    @Test
    fun `findClosestMap should return closest map coordinates`() {
        // Given
        val character = createTestCharacter(x = 5, y = 5)

        val maps = listOf(
            createTestMapData(x = 10, y = 10),
            createTestMapData(x = 3, y = 3),  // This should be the closest
            createTestMapData(x = 15, y = 15)
        )

        val response = ArtifactsArrayResponseBody(
            data = maps,
            total = maps.size,
            page = 1,
            size = maps.size,
            pages = 1
        )

        `when`(mapMongoClient.getMaps(
            content_type = null,
            content_code = null,
            page = 1,
            size = 100
        )).thenReturn(response)

        // When
        val result = mapProximityService.findClosestMap(character)

        // Then
        assertEquals(Pair(3, 3), result)
        verify(mapMongoClient).getMaps(
            content_type = null,
            content_code = null,
            page = 1,
            size = 100
        )
    }

    @Test
    fun `findClosestMap should return null when no maps are found`() {
        // Given
        val character = createTestCharacter(x = 5, y = 5)

        val response = ArtifactsArrayResponseBody<MapData>(
            data = emptyList(),
            total = 0,
            page = 1,
            size = 0,
            pages = 0
        )

        `when`(mapMongoClient.getMaps(
            content_type = null,
            content_code = null,
            page = 1,
            size = 100
        )).thenReturn(response)

        // When
        val result = mapProximityService.findClosestMap(character)

        // Then
        assertNull(result)
        verify(mapMongoClient).getMaps(
            content_type = null,
            content_code = null,
            page = 1,
            size = 100
        )
    }

    @Test
    fun `findClosestMap should apply content filters`() {
        // Given
        val character = createTestCharacter(x = 5, y = 5)
        val contentType = "resource"
        val contentCode = "iron"

        val maps = listOf(
            createTestMapData(x = 10, y = 10, contentType = contentType, contentCode = contentCode)
        )

        val response = ArtifactsArrayResponseBody(
            data = maps,
            total = maps.size,
            page = 1,
            size = maps.size,
            pages = 1
        )

        `when`(mapMongoClient.getMaps(
            content_type = contentType,
            content_code = contentCode,
            page = 1,
            size = 100
        )).thenReturn(response)

        // When
        val result = mapProximityService.findClosestMap(character, contentType, contentCode)

        // Then
        assertEquals(Pair(10, 10), result)
        verify(mapMongoClient).getMaps(
            content_type = contentType,
            content_code = contentCode,
            page = 1,
            size = 100
        )
    }

    // Helper methods to create test objects

    private fun createTestCharacter(x: Int, y: Int): ArtifactsCharacter {
        return ArtifactsCharacter(
            name = "TestCharacter",
            account = "TestAccount",
            level = 1,
            gold = 100,
            hp = 100,
            maxHp = 100,
            x = x,
            y = y,
            inventory = null,
            cooldown = 0,
            skin = "default",
            task = null,
            dmg = 10,
            wisdom = 10,
            prospecting = 10,
            criticalStrike = 10,
            speed = 10,
            haste = 10,
            xp = 0,
            maxXp = 100,
            taskType = null,
            taskTotal = 0,
            taskProgress = 0,
            miningXp = 0,
            miningMaxXp = 100,
            miningLevel = 1,
            woodcuttingXp = 0,
            woodcuttingMaxXp = 100,
            woodcuttingLevel = 1,
            fishingXp = 0,
            fishingMaxXp = 100,
            fishingLevel = 1,
            weaponcraftingXp = 0,
            weaponcraftingMaxXp = 100,
            weaponcraftingLevel = 1,
            gearcraftingXp = 0,
            gearcraftingMaxXp = 100,
            gearcraftingLevel = 1,
            jewelrycraftingXp = 0,
            jewelrycraftingMaxXp = 100,
            jewelrycraftingLevel = 1,
            cookingXp = 0,
            cookingMaxXp = 100,
            cookingLevel = 1,
            alchemyXp = 0,
            alchemyMaxXp = 100,
            alchemyLevel = 1,
            inventoryMaxItems = 20,
            attackFire = 0,
            attackEarth = 0,
            attackWater = 0,
            attackAir = 0,
            dmgFire = 0,
            dmgEarth = 0,
            dmgWater = 0,
            dmgAir = 0,
            resFire = 0,
            resEarth = 0,
            resWater = 0,
            resAir = 0,
            weaponSlot = null,
            runeSlot = null,
            shieldSlot = null,
            helmetSlot = null,
            bodyArmorSlot = null,
            legArmorSlot = null,
            bootsSlot = null,
            ring1Slot = null,
            ring2Slot = null,
            amuletSlot = null,
            artifact1Slot = null,
            artifact2Slot = null,
            artifact3Slot = null,
            utility1Slot = null,
            utility1SlotQuantity = 0,
            utility2Slot = null,
            utility2SlotQuantity = 0,
            bagSlot = null,
            cooldownExpiration = Instant.now()
        )
    }

    private fun createTestMapData(
        x: Int, 
        y: Int, 
        contentType: String? = null, 
        contentCode: String? = null
    ): MapData {
        val content = if (contentType != null && contentCode != null) {
            MapContent(type = contentType, code = contentCode)
        } else {
            null
        }

        return MapData(
            name = "map_${x}_${y}",
            skin = "default",
            x = x,
            y = y,
            content = content
        )
    }
}
