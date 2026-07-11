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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Invariant anti-duplication (cluster de minuit du 2026-07-11) : un nouveau thread de personnage
 * ne doit JAMAIS démarrer tant que l'ancien thread enregistré est vivant — deux threads pour le
 * même personnage produisent des 486 « action already in progress » et des désyncs 478/490/598.
 */
class ThreadServiceRestartDuplicationTest {

    private lateinit var minerJob: MinerJob
    private lateinit var threadService: ThreadService

    private val kepoConfig = CharacterConfig("Kepo", "vip_founder", "miner")

    /** Latch encore fermée en fin de test : à libérer pour que stopAllThreads ne traîne pas. */
    private val openOnTearDown = mutableListOf<CountDownLatch>()

    @BeforeEach
    fun setUp() {
        minerJob = mock(MinerJob::class.java)
        threadService = ThreadService(
            mock(CrafterJob::class.java), mock(FighterJob::class.java), mock(AlchemistJob::class.java),
            minerJob, mock(WoodworkerJob::class.java),
            MissionMetrics(SimpleMeterRegistry())
        )
        threadService.threadDeathTimeoutMs = 2000
    }

    @AfterEach
    fun tearDown() {
        openOnTearDown.forEach { it.countDown() }
        threadService.stopAllThreads()
    }

    @Test
    fun `restartCharacterThread interrompt l'ancien thread et attend sa mort avant d'en demarrer un nouveau`() {
        // given — chaque run dort longtemps (meurt sur interruption) ; on mesure le pic de runs concurrents
        val firstRunStarted = CountDownLatch(1)
        val secondRunStarted = CountDownLatch(1)
        val runCount = AtomicInteger(0)
        val concurrentRuns = AtomicInteger(0)
        val maxConcurrentRuns = AtomicInteger(0)
        `when`(minerJob.run("Kepo")).thenAnswer {
            val current = concurrentRuns.incrementAndGet()
            maxConcurrentRuns.updateAndGet { maxOf(it, current) }
            try {
                when (runCount.incrementAndGet()) {
                    1 -> firstRunStarted.countDown()
                    else -> secondRunStarted.countDown()
                }
                Thread.sleep(10_000)
            } finally {
                concurrentRuns.decrementAndGet()
            }
        }
        threadService.startCharacterThread(kepoConfig)
        assertThat(firstRunStarted.await(5, TimeUnit.SECONDS)).isTrue()

        // when
        val restarted = threadService.restartCharacterThread("Kepo")

        // then — le nouveau run démarre, et jamais deux runs n'ont coexisté
        assertThat(restarted).isTrue()
        assertThat(secondRunStarted.await(5, TimeUnit.SECONDS))
            .withFailMessage("Le second run n'a jamais démarré après le restart")
            .isTrue()
        assertThat(maxConcurrentRuns.get())
            .withFailMessage("Deux threads ont joué Kepo en même temps (pic = ${maxConcurrentRuns.get()})")
            .isEqualTo(1)
    }

    @Test
    fun `restartCharacterThread refuse de dupliquer quand l'ancien thread ne meurt pas`() {
        // given — le run avale les interruptions et ne rend la main qu'à la libération de la latch
        val firstRunStarted = CountDownLatch(1)
        val releaseJob = CountDownLatch(1)
        openOnTearDown.add(releaseJob)
        val runCount = AtomicInteger(0)
        `when`(minerJob.run("Kepo")).thenAnswer {
            runCount.incrementAndGet()
            firstRunStarted.countDown()
            while (releaseJob.count > 0) {
                try { releaseJob.await() } catch (_: InterruptedException) { /* insensible */ }
            }
        }
        threadService.startCharacterThread(kepoConfig)
        assertThat(firstRunStarted.await(5, TimeUnit.SECONDS)).isTrue()

        // when
        val restarted = threadService.restartCharacterThread("Kepo")

        // then — échec explicite, et surtout aucun second run lancé
        assertThat(restarted)
            .withFailMessage("restartCharacterThread doit échouer quand l'ancien thread refuse de mourir")
            .isFalse()
        assertThat(runCount.get())
            .withFailMessage("Un thread dupliqué a été démarré (runCount = ${runCount.get()})")
            .isEqualTo(1)
    }

    @Test
    fun `executeMissionSync abandonne la mission si le thread par defaut refuse de mourir`() {
        // given — même job insensible aux interruptions
        val firstRunStarted = CountDownLatch(1)
        val releaseJob = CountDownLatch(1)
        openOnTearDown.add(releaseJob)
        `when`(minerJob.run("Kepo")).thenAnswer {
            firstRunStarted.countDown()
            while (releaseJob.count > 0) {
                try { releaseJob.await() } catch (_: InterruptedException) { /* insensible */ }
            }
        }
        threadService.startCharacterThread(kepoConfig)
        assertThat(firstRunStarted.await(5, TimeUnit.SECONDS)).isTrue()
        val missionExecuted = AtomicBoolean(false)

        // when
        val result = threadService.executeMissionSync("Kepo") { missionExecuted.set(true) }

        // then — la mission est abandonnée plutôt qu'exécutée en concurrence avec le thread vivant
        assertThat(result).isFalse()
        assertThat(missionExecuted.get())
            .withFailMessage("La mission a été exécutée alors que le thread par défaut était toujours vivant")
            .isFalse()
    }
}
