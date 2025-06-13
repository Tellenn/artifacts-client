package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.MonsterClient
import com.tellenn.artifacts.clients.models.MonsterData
import com.tellenn.artifacts.clients.models.MonsterDrop
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.db.repositories.MonsterRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.Mockito.anyList

class MonsterSyncServiceTest {

    private lateinit var monsterSyncService: MonsterSyncService
    private lateinit var monsterRepository: MonsterRepository
    private lateinit var monsterClient: MonsterClient

    @BeforeEach
    fun setup() {
        // Create mocks
        monsterRepository = Mockito.mock(MonsterRepository::class.java)
        monsterClient = Mockito.mock(MonsterClient::class.java)

        // Create the service with mocked dependencies
        monsterSyncService = MonsterSyncService(monsterClient, monsterRepository)
    }

    @Test
    fun `should sync all monsters`() {
        // Given
        val testMonsterData1 = createTestMonsterData("monster1", "Goblin", 1)
        val testMonsterData2 = createTestMonsterData("monster2", "Orc", 5)
        val testMonsterData3 = createTestMonsterData("monster3", "Dragon", 20)

        val response =
            ArtifactsArrayResponseBody(listOf(testMonsterData1, testMonsterData2, testMonsterData3), 3, 1, 50, 1)

        // Create a mock that returns a response for the getMonsters call
        `when`(monsterClient.getMonsters(
            page = 1,
            size = 10
        )).thenReturn(response)

        // When
        val result = monsterSyncService.syncAllMonsters(10)

        // Then
        assertEquals(3, result)

        // Verify that deleteAll was called to clear the repository
        verify(monsterRepository).deleteAll()

        // Verify that saveAll was called with the list of monster documents
        verify(monsterRepository).saveAll(anyList())
    }

    @Test
    fun `should sync a single monster`() {
        // Given
        val testMonsterData = createTestMonsterData("monster1", "Goblin", 1)
        val response = ArtifactsResponseBody(testMonsterData)

        `when`(monsterClient.getMonster("monster1")).thenReturn(response)

        // When
        val result = monsterSyncService.syncMonster("monster1")

        // Then
        assertTrue(result)

        // Verify that the repository's save method was called with a MonsterDocument
        verify(monsterRepository).save(Mockito.argThat { monsterDocument ->
            monsterDocument.code == "monster1" &&
            monsterDocument.name == "Goblin" &&
            monsterDocument.level == 1 &&
            monsterDocument.hp == 100 &&
            monsterDocument.attackFire == 10 &&
            monsterDocument.attackWater == 10 &&
            monsterDocument.attackEarth == 10 &&
            monsterDocument.attackAir == 10 &&
            monsterDocument.defenseFire == 5 &&
            monsterDocument.defenseWater == 5 &&
            monsterDocument.defenseEarth == 5 &&
            monsterDocument.defenseAir == 5 &&
            monsterDocument.minGold == 10 &&
            monsterDocument.maxGold == 10 &&
            monsterDocument.drops?.size == 1 &&
            monsterDocument.drops?.get(0)?.code == "item1" &&
            monsterDocument.drops?.get(0)?.rate == 5
        })
    }

    @Test
    fun `should handle API error when syncing all monsters`() {
        // Given
        val testMonsterData1 = createTestMonsterData("monster1", "Goblin", 1)
        val testMonsterData2 = createTestMonsterData("monster2", "Orc", 5)

        val response1 = ArtifactsArrayResponseBody(listOf(testMonsterData1, testMonsterData2),2,1,50,2)

        // First page succeeds
        `when`(monsterClient.getMonsters(
            page = 1,
            size = 10
        )).thenReturn(response1)

        // Second page fails
        `when`(monsterClient.getMonsters(
            page = 2,
            size = 10
        )).thenThrow(RuntimeException("API Error"))

        // When
        val result = monsterSyncService.syncAllMonsters(10)

        // Then
        assertEquals(2, result)

        // Verify that deleteAll was called to clear the repository
        verify(monsterRepository).deleteAll()

        // Verify that saveAll was called for the successful monster data
        verify(monsterRepository).saveAll(anyList())
    }

    @Test
    fun `should handle API error when syncing a single monster`() {
        // Given
        `when`(monsterClient.getMonster("monster1")).thenThrow(RuntimeException("API Error"))

        // When
        val result = monsterSyncService.syncMonster("monster1")

        // Then
        assertFalse(result)

        // Verify that save was never called
        verify(monsterRepository, times(0)).save(Mockito.any())
    }

    private fun createTestMonsterData(code: String, name: String, level: Int): MonsterData {
        return MonsterData(
            code = code,
            name = name,
            level = level,
            hp = 100,
            attackFire = 10,
            attackWater = 10,
            attackEarth = 10,
            attackAir = 10,
            defenseFire = 5,
            defenseWater = 5,
            defenseEarth = 5,
            defenseAir = 5,
            minGold = 10,
            maxGold = 10,
            criticalStrike = 2,
            effects = emptyList(),
            drops = listOf(
                MonsterDrop(
                    code = "item1",
                    rate = 5,
                    minQuantity = 1,
                    maxQuantity = 1
                )
            )
        )
    }
}