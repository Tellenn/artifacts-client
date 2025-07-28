package com.tellenn.artifacts

import com.tellenn.artifacts.config.MongoTestConfiguration
import com.tellenn.artifacts.services.ItemSyncService
import com.tellenn.artifacts.services.MapSyncService
import com.tellenn.artifacts.services.sync.MonsterSyncService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Import(MongoTestConfiguration::class)
@Testcontainers
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

        @Bean
        @Primary
        fun mapSyncService(): MapSyncService {
            val mockMapSyncService = Mockito.mock(MapSyncService::class.java)
            Mockito.`when`(mockMapSyncService.syncWholeMap()).thenReturn(0)
            return mockMapSyncService
        }

        @Bean
        @Primary
        fun monsterSyncService(): MonsterSyncService {
            val mockMonsterSyncService = Mockito.mock(MonsterSyncService::class.java)
            Mockito.`when`(mockMonsterSyncService.syncAllMonsters()).thenReturn(0)
            return mockMonsterSyncService
        }
    }
}
