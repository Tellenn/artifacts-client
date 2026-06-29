package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.db.documents.GatheringTaskDocument
import com.tellenn.artifacts.db.documents.SliceReservation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Integration test for the atomic operations of [GatheringTaskRepositoryImpl] against a real MongoDB.
 *
 * A mocked `MongoTemplate` cannot validate `$pull` matching or aggregation-pipeline upsert semantics,
 * which is exactly where the two reviewed defects lived — so these run against a live server.
 *
 * Testcontainers cannot negotiate with Docker Desktop 29.x in the dev environment (its bundled
 * docker-java client fails the provider-strategy lookup), so this test connects to an externally
 * provided MongoDB instead. It is opt-in via the `mongo.it` system property and reads its URI from
 * `mongo.it.uri` (default `mongodb://localhost:27017/gathering_it`):
 *
 * ```
 * docker run -d -p 27018:27017 mongo:8.0
 * ./mvnw -o test -Dtest=GatheringTaskRepositoryIT -Dmongo.it=true -Dmongo.it.uri=mongodb://localhost:27018/gathering_it
 * ```
 *
 * In CI where Testcontainers works, this stays disabled unless `mongo.it` is set; point it at the
 * container's URI to enable it.
 */
@EnabledIfSystemProperty(named = "mongo.it", matches = "true")
class GatheringTaskRepositoryIT {

    private val mongoTemplate = MongoTemplate(
        SimpleMongoClientDatabaseFactory(
            System.getProperty("mongo.it.uri", "mongodb://localhost:27017/gathering_it")
        )
    )
    private val repository = GatheringTaskRepositoryImpl().apply { mongoTemplate = this@GatheringTaskRepositoryIT.mongoTemplate }

    @BeforeEach
    fun clear() {
        mongoTemplate.dropCollection(GatheringTaskDocument::class.java)
    }

    private fun find(code: String) = mongoTemplate.findById(code, GatheringTaskDocument::class.java)

    // --- upsertTarget (finding #2: atomic aggregation-pipeline upsert) ---

    @Test
    fun `upsertTarget inserts a new task when none exists`() {
        repository.upsertTarget("iron_bar", "mining", 10)

        val task = find("iron_bar")!!
        assertEquals(10, task.targetQuantity)
        assertEquals(10, task.remaining)
        assertEquals("mining", task.skill)
        assertEquals(0, task.producedQuantity)
    }

    @Test
    fun `upsertTarget raises target and remaining by the delta when quantity is higher`() {
        repository.upsertTarget("iron_bar", "mining", 10)

        repository.upsertTarget("iron_bar", "mining", 25)

        val task = find("iron_bar")!!
        assertEquals(25, task.targetQuantity)
        assertEquals(25, task.remaining)
    }

    @Test
    fun `upsertTarget does not lower the target when quantity is smaller`() {
        repository.upsertTarget("iron_bar", "mining", 25)

        repository.upsertTarget("iron_bar", "mining", 10)

        val task = find("iron_bar")!!
        assertEquals(25, task.targetQuantity)
        assertEquals(25, task.remaining)
    }

    @Test
    fun `upsertTarget raise preserves remaining already consumed by reservations`() {
        repository.upsertTarget("iron_bar", "mining", 10)
        repository.reserveSlice("iron_bar", "Kepo", 4) // remaining 10 -> 6

        repository.upsertTarget("iron_bar", "mining", 15) // +5 delta

        val task = find("iron_bar")!!
        assertEquals(15, task.targetQuantity)
        assertEquals(11, task.remaining) // 6 + 5
    }

    // --- reserveSlice ---

    @Test
    fun `reserveSlice grants up to remaining and returns a reservation with an id`() {
        repository.upsertTarget("iron_bar", "mining", 10)

        val reservation = repository.reserveSlice("iron_bar", "Kepo", 4)

        assertNotNull(reservation)
        assertEquals(4, reservation!!.amount)
        assertTrue(reservation.id.isNotBlank())
        assertEquals(6, find("iron_bar")!!.remaining)
    }

    @Test
    fun `reserveSlice caps the grant to remaining`() {
        repository.upsertTarget("iron_bar", "mining", 3)

        val reservation = repository.reserveSlice("iron_bar", "Kepo", 10)

        assertEquals(3, reservation!!.amount)
        assertEquals(0, find("iron_bar")!!.remaining)
    }

    @Test
    fun `reserveSlice returns null when nothing remains`() {
        repository.upsertTarget("iron_bar", "mining", 3)
        repository.reserveSlice("iron_bar", "Kepo", 3) // drains remaining to 0

        assertNull(repository.reserveSlice("iron_bar", "Kepo", 3))
    }

    // --- finding #1: two same-amount reservations addressed independently by id ---

    @Test
    fun `two same-amount reservations by one owner are removed independently by id`() {
        repository.upsertTarget("iron_bar", "mining", 10)
        val first = repository.reserveSlice("iron_bar", "Kepo", 3)!!
        val second = repository.reserveSlice("iron_bar", "Kepo", 3)!! // same amount, same owner, distinct id

        repository.reportProduced("iron_bar", first.id, 3) // report only the FIRST

        val task = find("iron_bar")!!
        assertEquals(1, task.reservations.size)          // only the first removed, not both
        assertEquals(second.id, task.reservations.first().id) // the second survives
        assertEquals(3, task.producedQuantity)           // credited exactly once
        assertEquals(4, task.remaining)                  // 10 - 3 - 3, untouched by the report
    }

    @Test
    fun `releaseSlice gives the reserved units back and removes only that reservation`() {
        repository.upsertTarget("iron_bar", "mining", 10)
        val reservation = repository.reserveSlice("iron_bar", "Kepo", 4)!! // remaining 6

        repository.releaseSlice("iron_bar", reservation.id, reservation.amount)

        val task = find("iron_bar")!!
        assertEquals(10, task.remaining)
        assertTrue(task.reservations.isEmpty())
    }

    @Test
    fun `reportProduced removes the task once the target is fully produced`() {
        repository.upsertTarget("iron_bar", "mining", 5)
        val reservation = repository.reserveSlice("iron_bar", "Kepo", 5)!!

        repository.reportProduced("iron_bar", reservation.id, 5)

        assertNull(find("iron_bar"))
    }

    @Test
    fun `expireStaleReservations restores only reservations older than the cutoff`() {
        repository.upsertTarget("iron_bar", "mining", 10)
        val fresh = repository.reserveSlice("iron_bar", "Kepo", 2)!! // remaining 8

        val stale = SliceReservation("Gustave", 3, Instant.now().minus(30, ChronoUnit.MINUTES))
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").`is`("iron_bar")),
            Update().inc("remaining", -3).push("reservations", stale),
            GatheringTaskDocument::class.java
        ) // remaining 5, two reservations

        repository.expireStaleReservations(Instant.now().minus(10, ChronoUnit.MINUTES))

        val task = find("iron_bar")!!
        assertEquals(8, task.remaining) // stale 3 returned, fresh 2 still held
        assertEquals(1, task.reservations.size)
        assertEquals(fresh.id, task.reservations.first().id)
    }
}
