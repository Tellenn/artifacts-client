package com.tellenn.artifacts.services

import java.util.concurrent.ConcurrentHashMap

/**
 * Backoff exponentiel des redémarrages de comportement par défaut.
 *
 * Un job qui meurt en boucle (recette hors de portée, désync banque, 429 dur…) est relancé par
 * [ThreadService] : sans amortisseur, le cycle crash → restart → crash tourne à ~1 s d'intervalle
 * et chaque itération refait des appels API (init, lectures banque, sélection) jusqu'à épuiser le
 * quota horaire. Le délai double à chaque crash « rapide » consécutif et se réarme dès qu'un
 * thread a vécu au moins [healthyUptimeMs] : un crash isolé après une longue vie saine repart du
 * délai de base.
 */
internal class RestartBackoff(
    private val baseDelayMs: Long,
    private val maxDelayMs: Long,
    private val healthyUptimeMs: Long,
) {
    private val consecutiveQuickFailures = ConcurrentHashMap<String, Int>()

    /** Délai à observer avant le prochain redémarrage de [characterName], selon son temps de vie [uptimeMs]. */
    fun nextDelayMs(characterName: String, uptimeMs: Long): Long {
        val previousFailures = if (uptimeMs >= healthyUptimeMs) 0 else consecutiveQuickFailures[characterName] ?: 0
        consecutiveQuickFailures[characterName] = previousFailures + 1
        // Borne sur le décalage : au-delà, le shift déborderait avant d'être plafonné.
        val delay = baseDelayMs shl previousFailures.coerceAtMost(MAX_SHIFT)
        return delay.coerceAtMost(maxDelayMs)
    }

    private companion object {
        const val MAX_SHIFT = 20
    }
}
