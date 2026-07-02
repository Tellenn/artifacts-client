package com.tellenn.artifacts.models

import java.time.Instant

/**
 * Vue synthétique d'une tâche du pool de récolte, exposée par l'API de statut de la file.
 */
data class GatheringTaskStatus(
    val materialCode: String,
    val skill: String,
    val targetQuantity: Int,
    val producedQuantity: Int,
    val remaining: Int,
    val reserved: Int,
    val progressPercent: Int,
    val reservations: List<ReservationStatus>,
    val createdAt: Instant,
)

/** Tranche réservée par un personnage, vue depuis l'API de statut. */
data class ReservationStatus(
    val owner: String,
    val amount: Int,
    val reservedAt: Instant,
)
