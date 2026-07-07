package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.EffectClient
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.db.documents.EffectDocument
import com.tellenn.artifacts.db.repositories.EffectRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class EffectSyncServiceTest {
    private lateinit var effectClient: EffectClient
    private lateinit var effectRepository: EffectRepository
    private lateinit var service: EffectSyncService

    @BeforeEach
    fun setUp() {
        effectClient = mock(EffectClient::class.java)
        effectRepository = mock(EffectRepository::class.java)
        service = EffectSyncService(effectClient, effectRepository)
    }

    private fun effect(code: String) = EffectDocument(code, code, "combat", "special", null)

    @Test
    fun `syncAllEffects clears then persists every fetched effect`() {
        `when`(effectClient.getEffects(page = anyInt(), size = anyInt()))
            .thenReturn(ArtifactsArrayResponseBody(listOf(effect("poison"), effect("burn")), 2, 1, 50, 1))

        val count = service.syncAllEffects()

        assertEquals(2, count)
        verify(effectRepository).deleteAll()
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<EffectDocument>>
        verify(effectRepository).saveAll(captor.capture())
        assertEquals(2, captor.value.size)
    }
}
