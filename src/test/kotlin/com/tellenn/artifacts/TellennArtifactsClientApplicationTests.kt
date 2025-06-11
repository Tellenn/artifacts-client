package com.tellenn.artifacts

import com.tellenn.artifacts.services.ItemSyncService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@SpringBootTest
class TellennArtifactsClientApplicationTests {

    @Suppress("EmptyMethod")
    @Test
    fun contextLoads() {
        // The mock is already configured in the TestConfig class
        // Just verify that the application context loads successfully
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun itemSyncService(): ItemSyncService {
            val mockItemSyncService = Mockito.mock(ItemSyncService::class.java)
            Mockito.`when`(mockItemSyncService.syncAllItems()).thenReturn(0)
            return mockItemSyncService
        }
    }
}
