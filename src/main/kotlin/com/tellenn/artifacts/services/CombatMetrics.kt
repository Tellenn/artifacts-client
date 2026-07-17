package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.responses.Fight
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Compte les combats résolus, ventilés par personnage, monstre et issue (win/loss).
 * Un incrément = un combat terminé : le total des issues "win" donne les monstres tués,
 * dérivé côté Grafana.
 */
@Component
class CombatMetrics(private val meterRegistry: MeterRegistry) {

    companion object {
        private const val COMBAT_RESULT_COUNTER = "artifacts.combat.result"
    }

    /**
     * Enregistre l'issue d'un combat. [character] est le personnage qui a déclenché le combat
     * (le meneur pour un combat de groupe) : un seul incrément par combat, pour ne pas gonfler
     * le total de kills lors des combats de boss à plusieurs. Un [fight] nul (réponse sans combat)
     * n'est pas compté.
     */
    fun recordFight(character: String, fight: Fight?) {
        if (fight == null) return
        Counter.builder(COMBAT_RESULT_COUNTER)
            .description("Combats résolus par personnage, monstre et issue")
            .tag("character", character)
            .tag("monster", fight.opponentCode)
            .tag("outcome", fight.result)
            .register(meterRegistry)
            .increment()
    }
}
