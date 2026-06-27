package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.MapClient
import com.tellenn.artifacts.models.*
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.db.clients.MapMongoClient
import com.tellenn.artifacts.db.repositories.TransitionMapperRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Instant

class MapProximityServiceTest {

    private lateinit var transitionMapperRepository: TransitionMapperRepository
    private lateinit var mapMongoClient: MapMongoClient
    private lateinit var accountClient: AccountClient
    private lateinit var mapClient: MapClient
    private lateinit var mapService: MapService

    @BeforeEach
    fun setUp() {
        mapMongoClient = mock(MapMongoClient::class.java)
        transitionMapperRepository = mock(TransitionMapperRepository::class.java)
        accountClient = mock(AccountClient::class.java)
        mapClient = mock(MapClient::class.java)
        mapService = MapService(mapMongoClient, transitionMapperRepository, accountClient, mapClient)
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
        val result = mapService.findClosestMap(character)

        // Then
        assertEquals(maps[1], result)
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
        val character = createTestCharacter(x = 6, y = 6)
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
        val result = mapService.findClosestMap(character, contentType, contentCode)

        // Then
        assertEquals(maps[0], result)
        verify(mapMongoClient).getMaps(
            content_type = contentType,
            content_code = contentCode,
            page = 1,
            size = 100
        )
    }

    @Test
    fun `findClosestMap should filter by achievements when checkAchievement is true`() {
        // Given
        val character = createTestCharacter(x = 5, y = 5)
        character.account = "TestAccount"

        val map1 = createTestMapData(x = 10, y = 10) // No access conditions
        val map2 = createLockedMapData(x = 3, y = 3, achievementCode = "locked_ach") // Locked by achievement

        val maps = listOf(map1, map2)

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

        // Mock achievements: character does NOT have "locked_ach"
        val achievementsResponse = ArtifactsArrayResponseBody(
            data = listOf(createAchievement("some_other_ach")),
            total = 1,
            page = 1,
            size = 1,
            pages = 1
        )
        `when`(accountClient.getAccountAchievements("TestAccount", true)).thenReturn(achievementsResponse)

        // When
        val result = mapService.findClosestMap(character, checkAchievement = true)

        // Then
        // map2 is closer but locked, so it should return map1
        assertEquals(map1, result)
    }

    @Test
    fun `findClosestMap should return closest map if achievement is unlocked`() {
        // Given
        val character = createTestCharacter(x = 5, y = 5)
        character.account = "TestAccount"

        val map1 = createTestMapData(x = 10, y = 10)
        val map2 = createLockedMapData(x = 3, y = 3, achievementCode = "locked_ach")

        val maps = listOf(map1, map2)

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

        // Mock achievements: character HAS "locked_ach"
        val achievementsResponse = ArtifactsArrayResponseBody(
            data = listOf(createAchievement("locked_ach")),
            total = 1,
            page = 1,
            size = 1,
            pages = 1
        )
        `when`(accountClient.getAccountAchievements("TestAccount", true)).thenReturn(achievementsResponse)

        // When
        val result = mapService.findClosestMap(character, checkAchievement = true)

        // Then
        // map2 is closer and unlocked
        assertEquals(map2, result)
    }

    @Test
    fun `findClosestMapFromApi should query the live API instead of the local cache`() {
        // Given — an event-based NPC whose closest live location is map2
        val character = createTestCharacter(x = 5, y = 5)
        val contentType = "npc"
        val contentCode = "santa_claus"

        val maps = listOf(
            createTestMapData(x = 12, y = 12, contentType = contentType, contentCode = contentCode),
            createTestMapData(x = 4, y = 4, contentType = contentType, contentCode = contentCode) // closest
        )
        val response = ArtifactsArrayResponseBody(
            data = maps, total = maps.size, page = 1, size = maps.size, pages = 1
        )
        `when`(mapClient.getMaps(
            name = null,
            content_type = contentType,
            content_code = contentCode,
            page = 1,
            size = 100
        )).thenReturn(response)

        // When
        val result = mapService.findClosestMapFromApi(character, contentType, contentCode)

        // Then — the live API is queried and the closest live location is returned
        assertEquals(maps[1], result)
        verify(mapClient).getMaps(
            name = null,
            content_type = contentType,
            content_code = contentCode,
            page = 1,
            size = 100
        )
    }

    // Helper methods to create test objects

    private fun createLockedMapData(x: Int, y: Int, achievementCode: String): MapData {
        val conditions = listOf(Conditions(code = achievementCode, operator = "achievement_unlocked", value = 0))
        val access = Access(type = "achievement", conditions = conditions)
        
        return MapData(
            name = "map_${x}_${y}",
            skin = "default",
            x = x,
            y = y,
            mapId = 1,
            layer = "main",
            access = access,
            interactions = null,
            region = 1
        )
    }

    private fun createAchievement(code: String): Achievement {
        return Achievement(
            name = "Achievement $code",
            code = code,
            description = "Desc",
            points = 10,
            objectives = emptyList(),
            rewards = Rewards(0,null),
            completedAt = "now"
        )
    }

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
            mapId = 1,
            layer = "main",
            inventory = arrayOf(),
            cooldown = 0,
            skin = "default",
            task = null,
            initiative = 10,
            threat = 0,
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
            utility1Slot = "",
            utility1SlotQuantity = 0,
            utility2Slot = "",
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
            mapId = 1,
            layer = "main",
            access = null,
            interactions = Interactions(content = content, transition = null),
            region = 1
        )
    }
}
