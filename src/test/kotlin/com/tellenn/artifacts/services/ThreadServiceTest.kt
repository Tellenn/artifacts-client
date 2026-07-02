package com.tellenn.artifacts.services

import com.tellenn.artifacts.config.CharacterConfig
import com.tellenn.artifacts.jobs.AlchemistJob
import com.tellenn.artifacts.jobs.CrafterJob
import com.tellenn.artifacts.jobs.FighterJob
import com.tellenn.artifacts.jobs.MinerJob
import com.tellenn.artifacts.jobs.WoodworkerJob
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ThreadServiceTest {

    private lateinit var crafterJob: CrafterJob
    private lateinit var fighterJob: FighterJob
    private lateinit var alchemistJob: AlchemistJob
    private lateinit var minerJob: MinerJob
    private lateinit var woodworkerJob: WoodworkerJob
    private lateinit var threadService: ThreadService

    private val kepoConfig = CharacterConfig("Kepo", "vip_founder", "miner")

    @BeforeEach
    fun setUp() {
        crafterJob = mock(CrafterJob::class.java)
        fighterJob = mock(FighterJob::class.java)
        alchemistJob = mock(AlchemistJob::class.java)
        minerJob = mock(MinerJob::class.java)
        woodworkerJob = mock(WoodworkerJob::class.java)
        threadService = ThreadService(
            crafterJob, fighterJob, alchemistJob, minerJob, woodworkerJob,
            MissionMetrics(SimpleMeterRegistry())
        )
    }

    @AfterEach
    fun tearDown() {
        threadService.stopAllThreads()
    }

    @Test
    fun `restarts the default behavior when a job dies on a socket timeout`() {
        // given — the first run dies on a SocketTimeoutException (which extends InterruptedIOException),
        // exactly as a timed-out OkHttp POST would surface up the job's call stack.
        val secondRunReached = CountDownLatch(1)
        val runCount = AtomicInteger(0)
        `when`(minerJob.run("Kepo")).thenAnswer {
            if (runCount.incrementAndGet() == 1) {
                throw SocketTimeoutException("timeout")
            }
            secondRunReached.countDown()
            // Keep the second run alive so the thread does not exit on normal completion.
            Thread.sleep(10_000)
        }

        // when
        threadService.startCharacterThread(kepoConfig)

        // then — the thread must come back to life rather than dying permanently.
        assertThat(secondRunReached.await(5, TimeUnit.SECONDS))
            .withFailMessage("Default behavior was not restarted after a socket timeout")
            .isTrue()
    }
}
