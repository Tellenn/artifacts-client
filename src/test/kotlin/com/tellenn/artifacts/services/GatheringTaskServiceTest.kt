package com.tellenn.artifacts.services

import com.tellenn.artifacts.db.documents.GatheringTaskDocument
import com.tellenn.artifacts.db.documents.SliceReservation
import com.tellenn.artifacts.db.repositories.GatheringTaskRepository
import com.tellenn.artifacts.models.GatheringTaskStatus
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.ReservationStatus
import com.tellenn.artifacts.exceptions.BattleLostException
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
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

    // --- produceOpenSlices : cycle réserve → produit → reporte, réutilisé par crafter et workers ---

    @Test
    fun `produceOpenSlices produit et reporte chaque tranche jusqu'a epuisement du pool`() {
        // given : deux tranches disponibles, puis plus rien à réserver
        val first = SliceReservation("Renoir", 30, Instant.now())
        val second = SliceReservation("Renoir", 20, Instant.now())
        `when`(repository.reserveSlice("iron_bar", "Renoir", 30)).thenReturn(first, second, null)

        // when
        val producedSlices = mutableListOf<Int>()
        val total = service.produceOpenSlices("iron_bar", "Renoir", 30) { producedSlices.add(it) }

        // then : chaque tranche accordée est produite puis reportée, le total est cumulé
        assertEquals(listOf(30, 20), producedSlices)
        assertEquals(50, total)
        verify(repository).reportProduced("iron_bar", first.id, 30)
        verify(repository).reportProduced("iron_bar", second.id, 20)
    }

    @Test
    fun `produceOpenSlices ne produit rien sans tache ouverte`() {
        // given : aucune tâche dans le pool pour ce matériau
        `when`(repository.reserveSlice("iron_bar", "Renoir", 30)).thenReturn(null)

        // when
        var produceCalled = false
        val total = service.produceOpenSlices("iron_bar", "Renoir", 30) { produceCalled = true }

        // then
        assertEquals(0, total)
        assertFalse(produceCalled)
    }

    @Test
    fun `produceOpenSlices libere la tranche et propage l'exception quand la production echoue`() {
        // given : une tranche réservée mais la production échoue (combat perdu)
        val reservation = SliceReservation("Renoir", 30, Instant.now())
        `when`(repository.reserveSlice("iron_bar", "Renoir", 30)).thenReturn(reservation)

        // when - then : l'exception remonte telle quelle
        assertThrows(BattleLostException::class.java) {
            service.produceOpenSlices("iron_bar", "Renoir", 30) { throw BattleLostException("slime") }
        }
        verify(repository).releaseSlice("iron_bar", reservation.id, 30)
        verify(repository, never()).reportProduced(anyString(), anyString(), anyInt())
    }

    // --- produceOpenSlices : les tranches mob sont bornées pour ne pas monopoliser une tâche ---

    @Test
    fun `produceOpenSlices borne les tranches mob a 20 unites`() {
        // given : une tâche mob de 145 unités, l'appelant demande tout d'un coup
        val mobTask = task("feather", "mob").copy(targetQuantity = 145, remaining = 145)
        `when`(repository.findById("feather")).thenReturn(java.util.Optional.of(mobTask))
        val reservation = SliceReservation("Cloud", 20, Instant.now())
        `when`(repository.reserveSlice("feather", "Cloud", 20)).thenReturn(reservation, null)

        // when
        val total = service.produceOpenSlices("feather", "Cloud", 145) { }

        // then : la réservation est demandée par tranches de 20, jamais de 145
        assertEquals(20, total)
        verify(repository, never()).reserveSlice("feather", "Cloud", 145)
    }

    @Test
    fun `produceOpenSlices ne borne pas les tranches des taches non-mob`() {
        // given : une tâche de minage, tranche demandée de 50
        val miningTask = task("iron_bar", "mining").copy(targetQuantity = 100, remaining = 100)
        `when`(repository.findById("iron_bar")).thenReturn(java.util.Optional.of(miningTask))
        `when`(repository.reserveSlice("iron_bar", "Kepo", 50)).thenReturn(null)

        // when
        service.produceOpenSlices("iron_bar", "Kepo", 50) { }

        // then : la tranche demandée est transmise sans plafond
        verify(repository).reserveSlice("iron_bar", "Kepo", 50)
    }

    @Test
    fun `produceOpenSlices s'arrete proprement quand l'infra du pool echoue`() {
        // given : le pool est injoignable (best-effort — la collecte directe prendra le relais)
        `when`(repository.reserveSlice("iron_bar", "Renoir", 30)).thenThrow(RuntimeException("mongo down"))

        // when
        var produceCalled = false
        val total = service.produceOpenSlices("iron_bar", "Renoir", 30) { produceCalled = true }

        // then : aucune exception ne remonte, rien n'est produit
        assertEquals(0, total)
        assertFalse(produceCalled)
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

    // --- expireStaleReservations : filet de sécurité pour les tranches orphelines (restart, release perdu) ---

    @Test
    fun `expireStaleReservations restitue les reservations plus vieilles que le TTL de 10 minutes`() {
        // when
        service.expireStaleReservations()

        // then : le cutoff transmis au repository est bien "maintenant - 10 minutes"
        val cutoff = org.mockito.Mockito.mockingDetails(repository).invocations
            .single { it.method.name == "expireStaleReservations" }
            .getArgument<Instant>(0)
        val expected = Instant.now().minus(java.time.Duration.ofMinutes(10))
        assertTrue(java.time.Duration.between(cutoff, expected).abs().seconds < 5)
    }

    @Test
    fun `expireStaleReservations est planifiee periodiquement et des le demarrage`() {
        // given : la méthode doit porter @Scheduled — sans ce câblage, les réservations
        // orphelines (thread interrompu, redémarrage de l'application) ne sont jamais restituées
        val scheduled = GatheringTaskService::class.java
            .getMethod("expireStaleReservations")
            .getAnnotation(org.springframework.scheduling.annotation.Scheduled::class.java)

        // then : exécution périodique, et premier passage immédiat (nettoyage post-redémarrage)
        org.junit.jupiter.api.Assertions.assertNotNull(scheduled, "expireStaleReservations doit être planifiée via @Scheduled")
        assertTrue(scheduled.fixedRate > 0, "le sweep doit être périodique (fixedRate)")
        assertTrue(scheduled.initialDelay <= 0, "le premier sweep doit s'exécuter dès le démarrage")
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
