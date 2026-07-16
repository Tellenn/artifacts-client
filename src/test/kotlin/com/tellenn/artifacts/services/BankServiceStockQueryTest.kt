package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * isInBank / availableQuantity doivent lire le cache Mongo (bankRepository) et non
 * l'API live : ces méthodes sont appelées dans les boucles de sélection des jobs et
 * représentaient ~1300 req/h sur /my/bank/items (65 % du quota horaire).
 */
class BankServiceStockQueryTest {

    private val bankClient = mock(BankClient::class.java)
    private val bankRepository = mock(BankItemRepository::class.java)
    private val itemRepository = mock(ItemRepository::class.java)
    private val characterService = mock(CharacterService::class.java)
    private val bankItemSyncService = mock(BankItemSyncService::class.java)
    private val accountClient = mock(AccountClient::class.java)
    private val movementClient = mock(MovementClient::class.java)
    private val mapService = mock(MapService::class.java)
    private val teleportService = mock(TeleportService::class.java)

    private val service = BankService(
        bankClient, bankRepository, itemRepository, characterService,
        bankItemSyncService, accountClient, movementClient, mapService, teleportService
    )

    @Test
    fun `isInBank lit le cache Mongo sans appeler l'API`() {
        // given
        `when`(bankRepository.findByCode("iron")).thenReturn(bankedItem("iron", 10))

        // when
        val result = service.isInBank("iron", 5)

        // then
        assertTrue(result)
        verify(bankClient, never()).getBankedItems(anyString(), anyInt())
    }

    @Test
    fun `isInBank retourne false quand l'item est absent du cache`() {
        // given
        `when`(bankRepository.findByCode("iron")).thenReturn(null)

        // when / then
        assertFalse(service.isInBank("iron", 1))
    }

    @Test
    fun `isInBank deduit les reservations du stock en cache`() {
        // given — 10 en banque mais 8 réservés : il n'en reste que 2 de disponibles
        `when`(bankRepository.findByCode("iron")).thenReturn(bankedItem("iron", 10))
        service.reserveInBank("iron", 8, "Renoir")

        // when / then
        assertFalse(service.isInBank("iron", 5))
    }

    @Test
    fun `availableQuantity lit le cache Mongo et deduit les reservations`() {
        // given
        `when`(bankRepository.findByCode("iron")).thenReturn(bankedItem("iron", 10))
        service.reserveInBank("iron", 3, "Renoir")

        // when
        val available = service.availableQuantity("iron")

        // then
        assertEquals(7, available)
        verify(bankClient, never()).getBankedItems(anyString(), anyInt())
    }

    @Test
    fun `availableQuantity retourne 0 quand l'item est absent du cache`() {
        // given
        `when`(bankRepository.findByCode("iron")).thenReturn(null)

        // when / then
        assertEquals(0, service.availableQuantity("iron"))
    }

    @Test
    fun `quantityInBank retourne la quantite brute sans deduire les reservations`() {
        // given — 10 en banque dont 8 réservés : on en POSSÈDE toujours 10
        `when`(bankRepository.findByCode("iron")).thenReturn(bankedItem("iron", 10))
        service.reserveInBank("iron", 8, "Renoir")

        // when / then
        assertEquals(10, service.quantityInBank("iron"))
        verify(bankClient, never()).getBankedItems(anyString(), anyInt())
    }

    @Test
    fun `quantityInBank retourne 0 quand l'item est absent du cache`() {
        // given
        `when`(bankRepository.findByCode("iron")).thenReturn(null)

        // when / then
        assertEquals(0, service.quantityInBank("iron"))
    }

    private fun bankedItem(code: String, quantity: Int): BankItemDocument =
        BankItemDocument(code, code, "", "resource", "", 1, true, true, null, null, null, quantity)
}
