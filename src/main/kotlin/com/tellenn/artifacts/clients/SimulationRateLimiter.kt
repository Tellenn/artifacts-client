package com.tellenn.artifacts.clients

import com.tellenn.artifacts.utils.TimeUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.Thread.sleep

/**
 * Étrangle proactivement les appels à `/simulation/fight` : l'endpoint impose « 1 req / s » et
 * renvoie un 429 dur sans pré-avertir via les en-têtes de rate limit (cf. le back-off réactif de
 * [BaseArtifactsClient]). Plutôt que d'encaisser puis re-tenter chaque 429, on garantit qu'au plus une
 * simulation part par seconde. La limite étant globale au token API (les 5 threads de personnages le
 * partagent), le compteur est partagé : chaque [acquire] réserve le prochain créneau d'une seconde de
 * façon atomique, puis attend son propre créneau hors verrou pour ne pas sérialiser inutilement.
 */
@Component
class SimulationRateLimiter(
    private val timeUtils: TimeUtils,
) {

    private val log = LoggerFactory.getLogger(SimulationRateLimiter::class.java)
    private val lock = Any()
    private var nextAllowedAtMs = 0L

    fun acquire() {
        val reservation = synchronized(lock) {
            val slot = reserveSimulationSlot(timeUtils.now().toEpochMilli(), nextAllowedAtMs, MIN_INTERVAL_MS)
            nextAllowedAtMs = slot.nextAllowedAtMs
            slot
        }
        if (reservation.waitMs > 0) {
            log.debug("Simulation throttle: waiting {} ms before next /simulation/fight", reservation.waitMs)
            sleep(reservation.waitMs)
        }
    }

    companion object {
        // L'API autorise 1 req/s sur /simulation/fight, en fenêtre glissante : espacer d'exactement
        // 1000 ms tombe sur la borne et 429 encore. On garde 100 ms de marge pour rester sous la limite.
        internal const val MIN_INTERVAL_MS = 1_100L
    }
}

/** Créneau réservé par [SimulationRateLimiter.acquire] : combien attendre, et le prochain créneau libre. */
internal data class SimulationSlot(val waitMs: Long, val nextAllowedAtMs: Long)

/**
 * Réserve le prochain créneau d'appel de simulation à partir de [nowMs]. Le créneau démarre au plus tôt
 * à [previousNextAllowedMs] (file d'attente : les threads concurrents s'espacent de [minIntervalMs]),
 * et [SimulationSlot.nextAllowedAtMs] avance d'un intervalle pour le prochain appelant. Fonction pure
 * pour être testable sans horloge réelle ni `sleep`.
 */
internal fun reserveSimulationSlot(nowMs: Long, previousNextAllowedMs: Long, minIntervalMs: Long): SimulationSlot {
    val waitMs = (previousNextAllowedMs - nowMs).coerceAtLeast(0)
    val nextAllowedAtMs = maxOf(nowMs, previousNextAllowedMs) + minIntervalMs
    return SimulationSlot(waitMs = waitMs, nextAllowedAtMs = nextAllowedAtMs)
}
