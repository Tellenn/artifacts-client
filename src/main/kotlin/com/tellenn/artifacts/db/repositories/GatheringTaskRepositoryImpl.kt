package com.tellenn.artifacts.db.repositories

import com.mongodb.client.model.UpdateOptions
import com.tellenn.artifacts.db.documents.GatheringTaskDocument
import com.tellenn.artifacts.db.documents.SliceReservation
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date

/**
 * Implémentation MongoTemplate des opérations atomiques du pool de tâches de récolte.
 */
@Component
class GatheringTaskRepositoryImpl : GatheringTaskRepositoryCustom {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    override fun reserveSlice(materialCode: String, owner: String, maxSlice: Int): SliceReservation? {
        repeat(MAX_RETRIES) {
            val task = mongoTemplate.findById(materialCode, GatheringTaskDocument::class.java)
            if (task == null || task.remaining <= 0) return null

            val granted = minOf(task.remaining, maxSlice)
            val reservation = SliceReservation(owner, granted, Instant.now())
            val query = Query.query(
                Criteria.where("_id").`is`(materialCode).and("remaining").gte(granted)
            )
            val update = Update()
                .inc("remaining", -granted)
                .push("reservations", reservation)

            val modified = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions().returnNew(true), GatheringTaskDocument::class.java
            )
            if (modified != null) return reservation
            // sinon : remaining a changé entre la lecture et l'écriture → on retente.
        }
        return null
    }

    override fun reportProduced(materialCode: String, reservationId: String, amount: Int) {
        val update = Update()
            .inc("producedQuantity", amount)
            .pull("reservations", reservationCriteria(reservationId))

        val result = mongoTemplate.findAndModify(
            Query.query(Criteria.where("_id").`is`(materialCode)),
            update,
            FindAndModifyOptions().returnNew(true),
            GatheringTaskDocument::class.java
        ) ?: return

        if (result.producedQuantity >= result.targetQuantity) {
            mongoTemplate.remove(result)
        }
    }

    override fun releaseSlice(materialCode: String, reservationId: String, amount: Int) {
        val update = Update()
            .inc("remaining", amount)
            .pull("reservations", reservationCriteria(reservationId))

        mongoTemplate.findAndModify(
            Query.query(Criteria.where("_id").`is`(materialCode)),
            update,
            FindAndModifyOptions().returnNew(true),
            GatheringTaskDocument::class.java
        )
    }

    override fun upsertTarget(materialCode: String, skill: String, quantity: Int) {
        val pipeline = listOf(
            Document(
                "\$set", Document()
                    .append(
                        "remaining", Document(
                            "\$add", listOf(
                                Document("\$ifNull", listOf("\$remaining", 0)),
                                Document(
                                    "\$subtract", listOf(
                                        Document("\$max", listOf(Document("\$ifNull", listOf("\$targetQuantity", 0)), quantity)),
                                        Document("\$ifNull", listOf("\$targetQuantity", 0))
                                    )
                                )
                            )
                        )
                    )
                    .append("targetQuantity", Document("\$max", listOf(Document("\$ifNull", listOf("\$targetQuantity", 0)), quantity)))
                    .append("skill", Document("\$ifNull", listOf("\$skill", skill)))
                    .append("producedQuantity", Document("\$ifNull", listOf("\$producedQuantity", 0)))
                    .append("createdAt", Document("\$ifNull", listOf("\$createdAt", Date.from(Instant.now()))))
            )
        )
        mongoTemplate.execute<Void?>(GatheringTaskDocument::class.java) { collection ->
            collection.updateOne(
                Document("_id", materialCode),
                pipeline,
                UpdateOptions().upsert(true)
            )
            null
        }
    }

    override fun releaseOrphanedReservations(activeIds: Set<String>, olderThan: Instant) {
        val staleTasks = mongoTemplate.find(
            Query.query(Criteria.where("reservations.reservedAt").lt(olderThan)),
            GatheringTaskDocument::class.java
        )
        for (task in staleTasks) {
            val orphanedReservations = task.reservations
                .filter { it.reservedAt.isBefore(olderThan) && it.id !in activeIds }
            for (reservation in orphanedReservations) {
                mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").`is`(task.materialCode)),
                    Update()
                        .inc("remaining", reservation.amount)
                        .pull("reservations", reservationCriteria(reservation.id)),
                    GatheringTaskDocument::class.java
                )
            }
        }
    }

    private fun reservationCriteria(reservationId: String): Query =
        Query.query(Criteria.where("id").`is`(reservationId))

    companion object {
        private const val MAX_RETRIES = 5
    }
}
