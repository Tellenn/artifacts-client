package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.MonsterClient
import com.tellenn.artifacts.clients.models.MonsterData
import com.tellenn.artifacts.clients.models.MonsterDrop
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.DataPage
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

        val dataPage = DataPage(
            items = listOf(testMonsterData1, testMonsterData2, testMonsterData3),
            total = 3,
            page = 1,
            size = 50,
            pages = 1
        )
        val response = ArtifactsResponseBody(dataPage)

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
            monsterDocument.id == "monster1" &&
            monsterDocument.name == "Goblin" &&
            monsterDocument.level == 1 &&
            monsterDocument.hp == 100 &&
            monsterDocument.attack == 10 &&
            monsterDocument.defense == 5 &&
            monsterDocument.speed == 8 &&
            monsterDocument.xpReward == 20 &&
            monsterDocument.goldReward == 10 &&
            monsterDocument.drops?.size == 1 &&
            monsterDocument.drops?.get(0)?.itemId == "item1" &&
            monsterDocument.drops?.get(0)?.chance == 0.5
        })
    }

    @Test
    fun `should handle API error when syncing all monsters`() {
        // Given
        val testMonsterData1 = createTestMonsterData("monster1", "Goblin", 1)
        val testMonsterData2 = createTestMonsterData("monster2", "Orc", 5)

        val dataPage1 = DataPage(
            items = listOf(testMonsterData1, testMonsterData2),
            total = 2,
            page = 1,
            size = 50,
            pages = 2
        )
        val response1 = ArtifactsResponseBody(dataPage1)

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

    private fun createTestMonsterData(id: String, name: String, level: Int): MonsterData {
        return MonsterData(
            id = id,
            name = name,
            level = level,
            hp = 100,
            attack = 10,
            defense = 5,
            speed = 8,
            xpReward = 20,
            goldReward = 10,
            drops = listOf(
                MonsterDrop(
                    itemId = "item1",
                    chance = 0.5
                )
            )
        )
    }
}