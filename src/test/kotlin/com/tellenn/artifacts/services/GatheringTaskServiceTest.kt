package com.tellenn.artifacts.services

import com.tellenn.artifacts.db.documents.GatheringTaskDocument
import com.tellenn.artifacts.db.documents.SliceReservation
import com.tellenn.artifacts.db.repositories.GatheringTaskRepository
import com.tellenn.artifacts.models.GatheringTaskStatus
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.ReservationStatus
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class GatheringTaskServiceTest {

    private lateinit var repository: GatheringTaskRepository
    private lateinit var materialResponsibility: MaterialResponsibility
    private lateinit var itemService: ItemService
    private lateinit var service: GatheringTaskService

    @BeforeEach
    fun setUp() {
        repository = mock(GatheringTaskRepository::class.java)
        materialResponsibility = mock(MaterialResponsibility::class.java)
        itemService = mock(ItemService::class.java)
        service = GatheringTaskService(repository, materialResponsibility, itemService)
    }

    private fun item(code: String, level: Int): ItemDetails =
        ItemDetails(
            code = code, name = code, description = "", type = "resource", subtype = "bar",
            level = level, tradeable = true, craft = null, effects = emptyList(), conditions = emptyList()
        )

    private fun task(code: String, skill: String): GatheringTaskDocument =
        GatheringTaskDocument(materialCode = code, skill = skill, targetQuantity = 10, remaining = 10)

    @Test
    fun `postShortfalls skips non-positive quantities and crafter-assembled materials`() {
        // given
        `when`(materialResponsibility.skillFor("iron_bar")).thenReturn("mining")
        `when`(materialResponsibility.skillFor("iron_sword")).thenReturn(null) // crafter-assembled

        // when
        service.postShortfalls(mapOf("iron_bar" to 5, "iron_sword" to 3, "ash_plank" to 0))

        // then : seul iron_bar est publié ; iron_sword (crafter) et ash_plank (qty 0) sont ignorés
        verify(repository).upsertTarget("iron_bar", "mining", 5)
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun `reserveSlice delegates to the repository`() {
        // given
        val reservation = com.tellenn.artifacts.db.documents.SliceReservation("Kepo", 30, java.time.Instant.now())
        `when`(repository.reserveSlice("iron_bar", "Kepo", 50)).thenReturn(reservation)

        // when
        val granted = service.reserveSlice("iron_bar", "Kepo", 50)

        // then
        assertEquals(reservation, granted)
        verify(repository).reserveSlice("iron_bar", "Kepo", 50)
    }

    @Test
    fun `reportProduced delegates to the repository`() {
        // when
        service.reportProduced("iron_bar", "res-1", 30)

        // then
        verify(repository).reportProduced("iron_bar", "res-1", 30)
    }

    @Test
    fun `releaseSlice delegates to the repository`() {
        // when
        service.releaseSlice("iron_bar", "res-1", 30)

        // then
        verify(repository).releaseSlice("iron_bar", "res-1", 30)
    }

    @Test
    fun `openTasksFor keeps tasks within the character level and drops the ones above`() {
        // given
        val reachable = task("iron_bar", "mining")
        val tooHigh = task("mithril_bar", "mining")
        `when`(repository.findBySkillInAndRemainingGreaterThan(listOf("mining"), 0))
            .thenReturn(listOf(reachable, tooHigh))
        `when`(itemService.getItem("iron_bar")).thenReturn(item("iron_bar", 10))
        `when`(itemService.getItem("mithril_bar")).thenReturn(item("mithril_bar", 40))

        // when
        val open = service.openTasksFor(listOf("mining"), mapOf("mining" to 20))

        // then
        assertEquals(1, open.size)
        assertEquals("iron_bar", open.first().materialCode)
        assertTrue(open.none { it.materialCode == "mithril_bar" })
    }

    @Test
    fun `getQueueStatus maps a task and its reservations into a status view`() {
        // given
        val reservedAt = Instant.parse("2026-07-02T10:00:00Z")
        val task = GatheringTaskDocument(
            materialCode = "iron_bar", skill = "mining",
            targetQuantity = 100, remaining = 30, producedQuantity = 40,
            reservations = listOf(
                SliceReservation("Kepo", 20, reservedAt),
                SliceReservation("Gustave", 10, reservedAt),
            )
        )
        `when`(repository.findAll()).thenReturn(listOf(task))

        // when
        val status = service.getQueueStatus().single()

        // then
        assertEquals(
            GatheringTaskStatus(
                materialCode = "iron_bar", skill = "mining",
                targetQuantity = 100, producedQuantity = 40, remaining = 30,
                reserved = 30, progressPercent = 40,
                reservations = listOf(
                    ReservationStatus("Kepo", 20, reservedAt),
                    ReservationStatus("Gustave", 10, reservedAt),
                ),
                createdAt = task.createdAt,
            ),
            status
        )
    }

    @Test
    fun `getQueueStatus lists oldest tasks first`() {
        // given
        val old = task("iron_bar", "mining").copy(createdAt = Instant.parse("2026-07-02T09:00:00Z"))
        val recent = task("ash_wood", "woodcutting").copy(createdAt = Instant.parse("2026-07-02T11:00:00Z"))
        `when`(repository.findAll()).thenReturn(listOf(recent, old))

        // when
        val codes = service.getQueueStatus().map { it.materialCode }

        // then
        assertEquals(listOf("iron_bar", "ash_wood"), codes)
    }

    @Test
    fun `getQueueStatus reports zero progress when target quantity is zero`() {
        // given
        `when`(repository.findAll()).thenReturn(listOf(task("iron_bar", "mining").copy(targetQuantity = 0)))

        // when
        val status = service.getQueueStatus().single()

        // then
        assertEquals(0, status.progressPercent)
    }

    @Test
    fun `getQueueStatus returns an empty list when the pool is empty`() {
        // given
        `when`(repository.findAll()).thenReturn(emptyList())

        // when
        val statuses = service.getQueueStatus()

        // then
        assertTrue(statuses.isEmpty())
    }
}
