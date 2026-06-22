package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.RaidClient
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.db.documents.RaidDocument
import com.tellenn.artifacts.db.repositories.RaidRepository
import com.tellenn.artifacts.models.Raid
import com.tellenn.artifacts.models.RaidSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class RaidSyncServiceTest {

    private lateinit var raidClient: RaidClient
    private lateinit var raidRepository: RaidRepository
    private lateinit var service: RaidSyncService

    @BeforeEach
    fun setUp() {
        raidClient = mock(RaidClient::class.java)
        raidRepository = mock(RaidRepository::class.java)
        service = RaidSyncService(raidClient, raidRepository)
    }

    private fun raid(code: String) = Raid(
        code = code, name = code, description = null, monster = "m_$code",
        schedule = RaidSchedule(listOf("monday"), 21, 0, 12), rewards = null,
    )

    @Test
    fun `syncAllRaids clears the collection and persists every fetched raid`() {
        `when`(raidClient.getRaids(name = isNull(), active = isNull(), page = anyInt(), size = anyInt()))
            .thenReturn(ArtifactsArrayResponseBody(listOf(raid("a"), raid("b")), total = 2, page = 1, size = 50, pages = 1))

        val count = service.syncAllRaids()

        assertEquals(2, count)
        verify(raidRepository).deleteAll()
        verify(raidRepository).saveAll(anyList<RaidDocument>())
    }
}
