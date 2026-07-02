package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.GatheringTaskDocument
import com.tellenn.artifacts.db.documents.SliceReservation
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Opérations atomiques personnalisées sur le pool de tâches de récolte.
 */
interface GatheringTaskRepositoryCustom {

    /**
     * Réserve atomiquement une tranche de [maxSlice] unités au plus pour [owner].
     * @return la réservation créée (avec son id et la quantité accordée), ou `null` si plus rien à réserver.
     */
    fun reserveSlice(materialCode: String, owner: String, maxSlice: Int): SliceReservation?

    /** Déclare [amount] unités effectivement produites pour la réservation [reservationId], qu'elle supprime. */
    fun reportProduced(materialCode: String, reservationId: String, amount: Int)

    /** Relâche la réservation [reservationId] : rend [amount] unités à `remaining`. */
    fun releaseSlice(materialCode: String, reservationId: String, amount: Int)

    /** Crée ou augmente la cible d'un matériau pour couvrir un nouveau manque. */
    fun upsertTarget(materialCode: String, skill: String, quantity: Int)

    /** Réclame les réservations orphelines plus anciennes que [olderThan]. */
    fun expireStaleReservations(olderThan: Instant)
}

@Repository
interface GatheringTaskRepository :
    MongoRepository<GatheringTaskDocument, String>,
    GatheringTaskRepositoryCustom {

    fun findBySkillInAndRemainingGreaterThan(skills: List<String>, remaining: Int): List<GatheringTaskDocument>
}
