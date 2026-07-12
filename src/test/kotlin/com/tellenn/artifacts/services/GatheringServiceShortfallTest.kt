package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CraftingClient
import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.RecipeIngredient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class GatheringServiceShortfallTest {

    private lateinit var bankService: BankService
    private lateinit var gatheringTaskService: GatheringTaskService
    private lateinit var materialResponsibility: MaterialResponsibility
    private lateinit var gatheringService: GatheringService

    @BeforeEach
    fun setUp() {
        bankService = mock(BankService::class.java)
        gatheringTaskService = mock(GatheringTaskService::class.java)
        materialResponsibility = mock(MaterialResponsibility::class.java)

        gatheringService = GatheringService(
            gatheringClient = mock(GatheringClient::class.java),
            mapService = mock(MapService::class.java),
            movementService = mock(MovementService::class.java),
            bankService = bankService,
            characterService = mock(CharacterService::class.java),
            craftingClient = mock(CraftingClient::class.java),
            resourceService = mock(ResourceService::class.java),
            itemService = mock(ItemService::class.java),
            battleService = mock(BattleService::class.java),
            equipmentService = mock(EquipmentService::class.java),
            accountClient = mock(AccountClient::class.java),
            npcClient = mock(NpcClient::class.java),
            grandExchangeService = mock(GrandExchangeService::class.java),
            gatheringTaskService = gatheringTaskService,
            materialResponsibility = materialResponsibility,
            characterContextService = mock(CharacterContextService::class.java),
        )
        // Default: nothing banked (availableQuantity = stock réservations déduites).
        `when`(bankService.availableQuantity(anyString())).thenReturn(0)
    }

    private fun item(vararg ingredients: Pair<String, Int>): ItemDetails =
        ItemDetails(
            code = "iron_dagger", name = "iron_dagger", description = "", type = "resource", subtype = "bar",
            level = 10, tradeable = true,
            craft = ItemCraft("weaponcrafting", 10, ingredients.map { RecipeIngredient(it.first, it.second) }, 1),
            effects = emptyList(), conditions = emptyList()
        )

    @Test
    fun `publie les manques quand deux materiaux delegables sont a produire`() {
        // given : deux ingrédients récoltables, rien en banque
        `when`(materialResponsibility.skillFor("iron_bar")).thenReturn("mining")
        `when`(materialResponsibility.skillFor("ash_plank")).thenReturn("woodcutting")

        // when
        gatheringService.postLevelingShortfalls(item("iron_bar" to 3, "ash_plank" to 2), batchSize = 4)

        // then : manques publiés avec la photo du stock banque au moment du post
        verify(gatheringTaskService).postShortfalls(
            mapOf("iron_bar" to 12, "ash_plank" to 8),
            mapOf("iron_bar" to 0, "ash_plank" to 0),
        )
    }

    @Test
    fun `le stock banque disponible (reservations deduites) est soustrait et publie avec la task`() {
        // given : 5 iron_bar disponibles en banque (réservations déjà déduites par availableQuantity)
        `when`(materialResponsibility.skillFor("iron_bar")).thenReturn("mining")
        `when`(materialResponsibility.skillFor("ash_plank")).thenReturn("woodcutting")
        `when`(bankService.availableQuantity("iron_bar")).thenReturn(5)

        // when : besoin de 12 iron_bar et 8 ash_plank
        gatheringService.postLevelingShortfalls(item("iron_bar" to 3, "ash_plank" to 2), batchSize = 4)

        // then : seuls les 7 manquants sont publiés, le stock banque est joint pour information
        verify(gatheringTaskService).postShortfalls(
            mapOf("iron_bar" to 7, "ash_plank" to 8),
            mapOf("iron_bar" to 5, "ash_plank" to 0),
        )
    }

    @Test
    fun `le stock banque est lu via availableQuantity et jamais via la quantite brute`() {
        // given : deux délégables — getOne (quantité brute, réservations ignorées) ne doit pas être consulté
        `when`(materialResponsibility.skillFor("iron_bar")).thenReturn("mining")
        `when`(materialResponsibility.skillFor("ash_plank")).thenReturn("woodcutting")

        // when
        gatheringService.postLevelingShortfalls(item("iron_bar" to 3, "ash_plank" to 2), batchSize = 4)

        // then
        verify(bankService, never()).getOne(anyString())
    }

    @Test
    fun `ne publie rien quand un seul materiau delegable est manquant`() {
        // given : un seul ingrédient récoltable
        `when`(materialResponsibility.skillFor("iron_bar")).thenReturn("mining")

        // when
        gatheringService.postLevelingShortfalls(item("iron_bar" to 3), batchSize = 4)

        // then : le crafter le collecte lui-même
        verify(gatheringTaskService, never()).postShortfalls(anyMap<String, Int>(), anyMap<String, Int>())
    }

    @Test
    fun `un materiau assemble par le crafter ne compte pas comme delegable`() {
        // given : un seul récoltable + un sous-composant assemblé par le crafter (skillFor null)
        `when`(materialResponsibility.skillFor("iron_bar")).thenReturn("mining")
        `when`(materialResponsibility.skillFor("iron_handle")).thenReturn(null)

        // when
        gatheringService.postLevelingShortfalls(item("iron_bar" to 3, "iron_handle" to 1), batchSize = 4)

        // then : un seul délégable ⇒ pas de publication
        verify(gatheringTaskService, never()).postShortfalls(anyMap<String, Int>(), anyMap<String, Int>())
    }

    @Test
    fun `une erreur de pool est avalee sans se propager`() {
        // given : deux délégables, mais le pool échoue
        `when`(materialResponsibility.skillFor("iron_bar")).thenReturn("mining")
        `when`(materialResponsibility.skillFor("ash_plank")).thenReturn("woodcutting")
        `when`(gatheringTaskService.postShortfalls(anyMap<String, Int>(), anyMap<String, Int>())).thenThrow(RuntimeException("mongo down"))

        // when - then : aucune exception ne remonte
        gatheringService.postLevelingShortfalls(item("iron_bar" to 3, "ash_plank" to 2), batchSize = 4)
    }
}
