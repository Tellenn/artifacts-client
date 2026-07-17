package com.tellenn.artifacts.services

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Compte les événements reçus par WebSocket (apparitions, disparitions, succès débloqués),
 * ventilés par type de message et par code d'événement. Les messages Grand Exchange
 * (ventes/achats) sont exclus à l'appel — ce ne sont pas des événements de jeu.
 */
@Component
class EventMetrics(private val meterRegistry: MeterRegistry) {

    companion object {
        private const val EVENT_COUNTER = "artifacts.event.received"
    }

    /**
     * Enregistre un événement reçu. [type] est le type de message WebSocket
     * (event_spawn, event_removed, achievement_unlocked) et [code] le code de l'événement
     * concerné (ex. strange_apparition), ou "-" lorsqu'il est absent du payload.
     */
    fun recordEvent(type: String, code: String) {
        Counter.builder(EVENT_COUNTER)
            .description("Événements WebSocket reçus (hors Grand Exchange), par type et code")
            .tag("type", type)
            .tag("code", code)
            .register(meterRegistry)
            .increment()
    }
}
