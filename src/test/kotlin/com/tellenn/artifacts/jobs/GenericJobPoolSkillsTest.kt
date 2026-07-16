package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.AppConfig
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Couvre le calcul des compétences du pool de récolte : un récolteur dont la compétence
 * est au niveau max aide aussi sur les tranches "mob" (matériaux de monstres).
 */
class GenericJobPoolSkillsTest {

    private lateinit var genericJob: GenericJob

    @BeforeEach
    fun setUp() {
        AppConfig.maxLevel = 50
        genericJob = GenericJob(
            mock(MapService::class.java),
            mock(MovementService::class.java),
            mock(BankService::class.java),
            mock(CharacterService::class.java),
            mock(AccountClient::class.java),
            mock(TaskService::class.java),
        )
    }

    @Test
    fun `le pool ne contient que la competence de recolte sous le niveau max`() {
        assertThat(genericJob.poolSkillsFor("mining", 49)).containsExactly("mining")
    }

    @Test
    fun `le pool inclut mob quand la competence de recolte est au niveau max`() {
        assertThat(genericJob.poolSkillsFor("mining", 50)).containsExactly("mining", "mob")
    }
}
