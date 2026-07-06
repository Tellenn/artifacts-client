package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CraftingClient
import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.RecipeIngredient
import com.tellenn.artifacts.models.SimpleItem
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.any
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Helpers null-safe pour les matchers Mockito sur des paramètres Kotlin non-nullables.
 * `Mockito.any()` renvoie `null`, ce que l'assertion de non-nullité Kotlin rejette ;
 * on enregistre le matcher puis on renvoie une valeur non vérifiée.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

private fun <T> eqObject(value: T): T {
    eq(value)
    return uninitialized()
}

/**
 * Couvre la phase de collecte du leveling par batch ([GatheringService.gatherLevelingMaterials]) :
 * le crafter consomme d'abord les tranches ouvertes du pool partagé (comme un worker), puis
 * complète directement ce que le pool et la banque ne couvrent pas.
 */
class GatheringServiceLevelingPoolTest {

    private lateinit var bankService: BankService
    private lateinit var gatheringTaskService: GatheringTaskService
    private lateinit var movementService: MovementService
    private lateinit var itemService: ItemService
    private lateinit var gatheringService: GatheringService
    private lateinit var character: ArtifactsCharacter

    @BeforeEach
    fun setUp() {
        bankService = mock(BankService::class.java)
        gatheringTaskService = mock(GatheringTaskService::class.java)
        movementService = mock(MovementService::class.java)
        itemService = mock(ItemService::class.java)
        character = character(inventoryMaxItems = 100)

        gatheringService = spy(
            GatheringService(
                gatheringClient = mock(GatheringClient::class.java),
                mapService = mock(MapService::class.java),
                movementService = movementService,
                bankService = bankService,
                characterService = mock(CharacterService::class.java),
                craftingClient = mock(CraftingClient::class.java),
                resourceService = mock(ResourceService::class.java),
                itemService = itemService,
                battleService = mock(BattleService::class.java),
                equipmentService = mock(EquipmentService::class.java),
                accountClient = mock(AccountClient::class.java),
                npcClient = mock(NpcClient::class.java),
                grandExchangeService = mock(GrandExchangeService::class.java),
                gatheringTaskService = gatheringTaskService,
                materialResponsibility = mock(MaterialResponsibility::class.java),
                characterContextService = mock(CharacterContextService::class.java),
            )
        )

        `when`(movementService.moveToBank(anyObject(), anyBoolean())).thenReturn(character)
        `when`(bankService.emptyInventory(anyObject())).thenReturn(character)
        `when`(itemService.getItem("iron_bar")).thenReturn(rawItem("iron_bar"))
        // La collecte réelle est hors sujet ici : craftOrGather rend le personnage inchangé.
        doAnswer { it.getArgument(0) }.`when`(gatheringService)
            .craftOrGather(anyObject(), anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean())
    }

    // iron_dagger = 3 iron_bar ; batch de 4 → besoin total de 12 iron_bar.
    private fun ironDagger(): ItemDetails =
        ItemDetails(
            code = "iron_dagger", name = "iron_dagger", description = "", type = "weapon", subtype = "",
            level = 10, tradeable = true,
            craft = ItemCraft("weaponcrafting", 10, listOf(RecipeIngredient("iron_bar", 3)), 1),
            effects = emptyList(), conditions = emptyList()
        )

    private fun rawItem(code: String): ItemDetails =
        ItemDetails(
            code = code, name = code, description = "", type = "resource", subtype = "bar",
            level = 10, tradeable = true, craft = null, effects = emptyList(), conditions = emptyList()
        )

    private fun bankHas(code: String, quantity: Int) {
        `when`(bankService.getOne(code)).thenReturn(SimpleItem(code, quantity))
    }

    @Test
    fun `le crafter consomme les tranches du pool avec le chunk d'inventaire comme taille max`() {
        // given : footprint 2 → chunk = 100 / 2 = 50 ; le pool produit tout, la banque est complète
        `when`(itemService.getInvSizeToCraft(anyObject())).thenReturn(2)
        `when`(gatheringTaskService.produceOpenSlices(anyString(), anyString(), anyInt(), anyObject()))
            .thenReturn(12)
        bankHas("iron_bar", 12)

        // when
        gatheringService.gatherLevelingMaterials(character, ironDagger(), quantity = 4, allowFight = false)

        // then : le crafter s'est présenté au pool en son nom, borné par son inventaire
        verify(gatheringTaskService).produceOpenSlices(eqObject("iron_bar"), eqObject("Renoir"), eq(50), anyObject())
        verify(gatheringService, never())
            .craftOrGather(anyObject(), anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean())
    }

    @Test
    fun `une tranche accordee est collectee puis deposee en banque`() {
        // given : le pool accorde une tranche de 10 via la lambda de production
        `when`(itemService.getInvSizeToCraft(anyObject())).thenReturn(2)
        `when`(gatheringTaskService.produceOpenSlices(anyString(), anyString(), anyInt(), anyObject()))
            .thenAnswer { invocation ->
                invocation.getArgument<(Int) -> Unit>(3).invoke(10)
                10
            }
        bankHas("iron_bar", 12)

        // when
        gatheringService.gatherLevelingMaterials(character, ironDagger(), quantity = 4, allowFight = false)

        // then : la tranche est collectée puis déposée
        verify(gatheringService)
            .craftOrGather(anyObject(), eqObject("iron_bar"), eq(10), anyInt(), anyBoolean(), anyBoolean())
        verify(movementService, atLeastOnce()).moveToBank(anyObject(), anyBoolean())
        verify(bankService, atLeastOnce()).emptyInventory(anyObject())
    }

    @Test
    fun `le manquant apres le pool est collecte directement`() {
        // given : pool sans tâche pour ce matériau, 5 en banque sur les 12 requis
        `when`(itemService.getInvSizeToCraft(anyObject())).thenReturn(2)
        `when`(gatheringTaskService.produceOpenSlices(anyString(), anyString(), anyInt(), anyObject()))
            .thenReturn(0)
        bankHas("iron_bar", 5)

        // when
        gatheringService.gatherLevelingMaterials(character, ironDagger(), quantity = 4, allowFight = false)

        // then : seuls les 7 manquants sont collectés
        verify(gatheringService)
            .craftOrGather(anyObject(), eqObject("iron_bar"), eq(7), anyInt(), anyBoolean(), anyBoolean())
    }

    @Test
    fun `sans tache pool ni stock banque la collecte directe par chunks est preservee`() {
        // given : footprint 20 → chunk = 100 / 20 = 5 ; rien en banque, rien dans le pool
        `when`(itemService.getInvSizeToCraft(anyObject())).thenReturn(20)
        `when`(gatheringTaskService.produceOpenSlices(anyString(), anyString(), anyInt(), anyObject()))
            .thenReturn(0)
        bankHas("iron_bar", 0)

        // when
        gatheringService.gatherLevelingMaterials(character, ironDagger(), quantity = 4, allowFight = false)

        // then : les 12 sont collectés par chunks de 5 (5 + 5 + 2)
        verify(gatheringService, times(2))
            .craftOrGather(anyObject(), eqObject("iron_bar"), eq(5), anyInt(), anyBoolean(), anyBoolean())
        verify(gatheringService)
            .craftOrGather(anyObject(), eqObject("iron_bar"), eq(2), anyInt(), anyBoolean(), anyBoolean())
    }

    private fun character(inventoryMaxItems: Int) = ArtifactsCharacter(
        name = "Renoir", account = "tellenn", level = 20, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = arrayOf(), cooldown = 0, skin = null, task = null,
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0, criticalStrike = 0, speed = 0,
        haste = 0, xp = 0, maxXp = 100, taskType = null, taskTotal = 0, taskProgress = 0,
        miningXp = 0, miningMaxXp = 100, miningLevel = 20, woodcuttingXp = 0, woodcuttingMaxXp = 100,
        woodcuttingLevel = 1, fishingXp = 0, fishingMaxXp = 100, fishingLevel = 1,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 100, weaponcraftingLevel = 1,
        gearcraftingXp = 0, gearcraftingMaxXp = 100, gearcraftingLevel = 1,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 100, jewelrycraftingLevel = 1,
        cookingXp = 0, cookingMaxXp = 100, cookingLevel = 1, alchemyXp = 0, alchemyMaxXp = 100, alchemyLevel = 1,
        inventoryMaxItems = inventoryMaxItems, attackFire = 0, attackEarth = 0, attackWater = 0, attackAir = 0,
        dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0, resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
        weaponSlot = null, runeSlot = null, shieldSlot = null, helmetSlot = null, bodyArmorSlot = null,
        legArmorSlot = null, bootsSlot = null, ring1Slot = null, ring2Slot = null, amuletSlot = null,
        artifact1Slot = null, artifact2Slot = null, artifact3Slot = null,
        utility1Slot = "", utility1SlotQuantity = 0, utility2Slot = "", utility2SlotQuantity = 0,
        bagSlot = null, cooldownExpiration = Instant.now(),
    )
}
