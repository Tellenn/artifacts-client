package com.tellenn.artifacts.db.documents

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

/**
 * Tâche de récolte partagée : un matériau que le crafter a besoin de voir disponible en banque.
 *
 * `materialCode` sert d'`@Id` : un seul document par matériau (déduplication naturelle).
 * `remaining` est la quantité encore non réservée, tirée atomiquement par les workers.
 * `bankQuantityAtPost` est la photo du stock banque disponible au moment du dernier post
 * (information : le besoin publié est déjà net de ce stock).
 */
@Document(collection = "gathering_tasks")
data class GatheringTaskDocument(
    @Id val materialCode: String,
    val skill: String,
    val targetQuantity: Int,
    val remaining: Int,
    val producedQuantity: Int = 0,
    val bankQuantityAtPost: Int = 0,
    val reservations: List<SliceReservation> = emptyList(),
    val createdAt: Instant = Instant.now()
)

/** Réservation d'une tranche (slice) en cours de production par un worker. */
data class SliceReservation(
    val owner: String,
    val amount: Int,
    val reservedAt: Instant,
    val id: String = UUID.randomUUID().toString()
)
