package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CraftingClient
import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.CraftingResponseBody
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.BankDetails
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.models.RecipeIngredient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class GatheringServiceRecycleTest {

    private lateinit var mapService: MapService
    private lateinit var movementService: MovementService
    private lateinit var bankService: BankService
    private lateinit var craftingClient: CraftingClient
    private lateinit var gatheringService: GatheringService

    private val characterName = "Renoir"

    @BeforeEach
    fun setUp() {
        mapService = mock(MapService::class.java)
        movementService = mock(MovementService::class.java)
        bankService = mock(BankService::class.java)
        craftingClient = mock(CraftingClient::class.java)

        gatheringService = GatheringService(
            gatheringClient = mock(GatheringClient::class.java),
            mapService = mapService,
            movementService = movementService,
            bankService = bankService,
            characterService = mock(CharacterService::class.java),
            craftingClient = craftingClient,
            resourceService = mock(ResourceService::class.java),
            itemService = mock(ItemService::class.java),
            battleService = mock(BattleService::class.java),
            equipmentService = mock(EquipmentService::class.java),
            accountClient = mock(AccountClient::class.java),
            npcClient = mock(NpcClient::class.java),
            grandExchangeService = mock(GrandExchangeService::class.java),
            gatheringTaskService = mock(GatheringTaskService::class.java),
            materialResponsibility = mock(MaterialResponsibility::class.java),
        )
    }

    @Test
    fun `recycle uses enhanced for level 20+ gear when the bank can pay the cost`() {
        // given : épée niv. 25, recette de 8 ingrédients au total, 1 unité recyclée
        // coût attendu = 8 * 1 * 10 (tarif niv. 21-30) = 80 or
        val item = gear(code = "iron_sword", level = 25, ingredients = mapOf("iron" to 6, "wood" to 2))
        val character = stubCharacter()
        stubBankGold(30000)
        stubFlow(character, code = "iron_sword", quantity = 1, enhanced = true)

        // when
        gatheringService.recycle(character, item, 1)

        // then
        verify(bankService).withdrawMoney(character, 80)
        verify(craftingClient).recycle(characterName, "iron_sword", 1, enhanced = true)
    }

    @Test
    fun `recycle stays normal when the bank gold is below the minimum threshold`() {
        // given : niv. 25 mais seulement 15 000 or en banque (< 20 000)
        val item = gear(code = "iron_sword", level = 25, ingredients = mapOf("iron" to 6, "wood" to 2))
        val character = stubCharacter()
        stubBankGold(15000)
        stubFlow(character, code = "iron_sword", quantity = 1, enhanced = false)

        // when
        gatheringService.recycle(character, item, 1)

        // then
        verify(bankService, never()).withdrawMoney(character, 80)
        verify(craftingClient).recycle(characterName, "iron_sword", 1, enhanced = false)
    }

    @Test
    fun `recycle stays normal for gear below level 20`() {
        // given : niv. 15, banque pleine
        val item = gear(code = "wooden_shield", level = 15, ingredients = mapOf("wood" to 4))
        val character = stubCharacter()
        stubBankGold(50000)
        stubFlow(character, code = "wooden_shield", quantity = 1, enhanced = false)

        // when
        gatheringService.recycle(character, item, 1)

        // then
        verify(craftingClient).recycle(characterName, "wooden_shield", 1, enhanced = false)
    }

    private fun stubCharacter(): ArtifactsCharacter {
        val character = mock(ArtifactsCharacter::class.java)
        `when`(character.name).thenReturn(characterName)
        return character
    }

    private fun stubBankGold(gold: Int) {
        val bankDetails = mock(BankDetails::class.java)
        `when`(bankDetails.gold).thenReturn(gold)
        `when`(bankService.getBankDetails()).thenReturn(bankDetails)
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubFlow(character: ArtifactsCharacter, code: String, quantity: Int, enhanced: Boolean) {
        val mapData = mock(MapData::class.java)
        `when`(mapData.mapId).thenReturn(42)
        `when`(mapService.findClosestMap(character = character, contentCode = "weaponcrafting")).thenReturn(mapData)
        `when`(movementService.moveToBank(character)).thenReturn(character)
        `when`(movementService.moveCharacterToCell(42, character)).thenReturn(character)
        `when`(bankService.withdrawMoney(character, 80)).thenReturn(character)

        val body = mock(CraftingResponseBody::class.java)
        `when`(body.character).thenReturn(character)
        val response = mock(ArtifactsResponseBody::class.java) as ArtifactsResponseBody<CraftingResponseBody>
        `when`(response.data).thenReturn(body)
        `when`(craftingClient.recycle(characterName, code, quantity, enhanced)).thenReturn(response)
    }

    private fun gear(code: String, level: Int, ingredients: Map<String, Int>): ItemDetails =
        ItemDetails(
            code = code,
            name = code,
            description = "",
            type = "weapon",
            subtype = "",
            level = level,
            tradeable = true,
            recyclable = true,
            craft = ItemCraft(
                skill = "weaponcrafting",
                level = level,
                items = ingredients.map { RecipeIngredient(it.key, it.value) },
                quantity = 1,
            ),
            effects = null,
            conditions = null,
        )
}
