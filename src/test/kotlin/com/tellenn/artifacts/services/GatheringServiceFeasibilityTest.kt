package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CraftingClient
import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.exceptions.BattleLostException
import com.tellenn.artifacts.exceptions.CharacterSkillTooLow
import com.tellenn.artifacts.exceptions.UnknownMapException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.RecipeIngredient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

/**
 * Helpers null-safe pour les matchers Mockito sur des paramètres Kotlin non-nullables :
 * `Mockito.any()` renvoie `null`, ce que l'assertion de non-nullité Kotlin rejette.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

/**
 * Garde-fou de faisabilité : la recette entière est vérifiée AVANT toute collecte,
 * pour ne pas découvrir un composant hors de portée après des heures de récolte.
 */
class GatheringServiceFeasibilityTest {

    private lateinit var bankService: BankService
    private lateinit var itemService: ItemService
    private lateinit var battleService: BattleService
    private lateinit var gatheringClient: GatheringClient
    private lateinit var gatheringService: GatheringService

    private val character = character()

    @BeforeEach
    fun setUp() {
        bankService = mock(BankService::class.java)
        itemService = mock(ItemService::class.java)
        battleService = mock(BattleService::class.java)
        gatheringClient = mock(GatheringClient::class.java)

        gatheringService = GatheringService(
            gatheringClient = gatheringClient,
            mapService = mock(MapService::class.java),
            movementService = mock(MovementService::class.java),
            bankService = bankService,
            characterService = mock(CharacterService::class.java),
            craftingClient = mock(CraftingClient::class.java),
            resourceService = mock(ResourceService::class.java),
            itemService = itemService,
            battleService = battleService,
            equipmentService = mock(EquipmentService::class.java),
            accountClient = mock(AccountClient::class.java),
            npcClient = mock(NpcClient::class.java),
            grandExchangeService = mock(GrandExchangeService::class.java),
            gatheringTaskService = mock(GatheringTaskService::class.java),
            materialResponsibility = mock(MaterialResponsibility::class.java),
            characterContextService = mock(CharacterContextService::class.java),
        )

        // Par défaut, les monstres qui droppent les ingrédients sont présents sur la carte
        `when`(battleService.isMonsterForItemOnMap(anyString())).thenReturn(true)

        `when`(itemService.getInvSizeToCraft(anyObject())).thenReturn(1)
        `when`(itemService.getItem("iron_dagger")).thenReturn(
            item("iron_dagger", craft = ItemCraft("weaponcrafting", 10, listOf(
                RecipeIngredient("iron_ore", 2),
                RecipeIngredient("ogre_eye", 1),
            ), 1))
        )
        `when`(itemService.getItem("iron_ore")).thenReturn(item("iron_ore", subtype = "mining", level = 5))
        `when`(itemService.getItem("ogre_eye")).thenReturn(item("ogre_eye", subtype = "mob"))
    }

    @Test
    fun `craftOrGather echoue avant toute collecte quand un ingredient exige un combat perdu d'avance`() {
        // given — l'ogre_eye (2e ingrédient) demande un combat que la simulation juge perdu
        `when`(battleService.isFightForItemWinnable(anyObject(), anyString())).thenReturn(false)

        // when / then — l'échec doit précéder la moindre récolte d'iron_ore
        assertThrows(BattleLostException::class.java) {
            gatheringService.craftOrGather(character, "iron_dagger", 1, allowFight = true, shouldTrain = false)
        }
        verifyNoInteractions(gatheringClient)
    }

    @Test
    fun `craftOrGather echoue immediatement quand le niveau de recolte d'un ingredient est insuffisant`() {
        // given — le 2e ingrédient (copper_ore) exige mining 25, le personnage est mining 20 :
        // sans garde-fou, l'iron_ore (1er ingrédient) serait déjà récolté avant l'échec
        `when`(itemService.getItem("iron_dagger")).thenReturn(
            item("iron_dagger", craft = ItemCraft("weaponcrafting", 10, listOf(
                RecipeIngredient("iron_ore", 2),
                RecipeIngredient("copper_ore", 3),
            ), 1))
        )
        `when`(itemService.getItem("copper_ore")).thenReturn(item("copper_ore", subtype = "mining", level = 25))

        // when / then
        assertThrows(CharacterSkillTooLow::class.java) {
            gatheringService.craftOrGather(character, "iron_dagger", 1, allowFight = true, shouldTrain = false)
        }
        verifyNoInteractions(gatheringClient)
    }

    @Test
    fun `craftOrGather echoue immediatement quand le niveau de craft d'un sous-composant est insuffisant`() {
        // given — la recette exige weaponcrafting 35, le personnage est weaponcrafting 20
        `when`(itemService.getItem("iron_dagger")).thenReturn(
            item("iron_dagger", craft = ItemCraft("weaponcrafting", 35, listOf(
                RecipeIngredient("iron_ore", 2),
            ), 1))
        )

        // when / then
        assertThrows(CharacterSkillTooLow::class.java) {
            gatheringService.craftOrGather(character, "iron_dagger", 1, allowFight = true, shouldTrain = false)
        }
        verifyNoInteractions(gatheringClient)
    }

    @Test
    fun `un ingredient deja couvert par la banque n'est pas verifie`() {
        // given — l'ogre_eye (1er ingrédient) est en banque : pas besoin d'évaluer le combat ;
        // le copper_ore trop haut niveau fait quand même échouer la recette, avant toute réservation
        `when`(itemService.getItem("iron_dagger")).thenReturn(
            item("iron_dagger", craft = ItemCraft("weaponcrafting", 10, listOf(
                RecipeIngredient("ogre_eye", 1),
                RecipeIngredient("copper_ore", 3),
            ), 1))
        )
        `when`(bankService.availableQuantity("ogre_eye")).thenReturn(1)
        `when`(itemService.getItem("copper_ore")).thenReturn(item("copper_ore", subtype = "mining", level = 25))

        // when / then
        assertThrows(CharacterSkillTooLow::class.java) {
            gatheringService.craftOrGather(character, "iron_dagger", 1, allowFight = true, shouldTrain = false)
        }
        verify(battleService, never()).isFightForItemWinnable(anyObject(), anyString())
        verify(bankService, never()).reserveInBank(anyString(), anyInt(), anyString())
    }

    @Test
    fun `isRecipeObtainable renvoie false quand un sous-composant exige un craft hors de portee`() {
        // given — cas Aerith/antidote : l'ingrédient maple_sap se craft en woodcutting 40,
        // le personnage est woodcutting 1 et la banque est vide
        `when`(itemService.getItem("antidote")).thenReturn(
            item("antidote", subtype = "potion", craft = ItemCraft("weaponcrafting", 10, listOf(
                RecipeIngredient("maple_sap", 1),
            ), 1))
        )
        `when`(itemService.getItem("maple_sap")).thenReturn(
            item("maple_sap", subtype = "sap", craft = ItemCraft("woodcutting", 40, listOf(
                RecipeIngredient("maple_wood", 15),
            ), 1))
        )

        // when / then
        assertFalse(gatheringService.isRecipeObtainable(character, "antidote", 1))
    }

    @Test
    fun `isRecipeObtainable ignore le stock banque de l'item final car le craft racine le produit toujours`() {
        // given — boucle antidote Aerith (2026-07-16) : la banque couvre l'item FINAL (20 antidotes),
        // mais resolveCraftOrGather au niveau racine produit toujours l'item au lieu de le retirer
        // de la banque ; se satisfaire du stock banque du nœud racine rend la recette "obtenable"
        // alors que l'exécution échoue sur maple_sap (woodcutting 40, personnage woodcutting 1)
        `when`(itemService.getItem("antidote")).thenReturn(
            item("antidote", subtype = "potion", craft = ItemCraft("alchemy", 1, listOf(
                RecipeIngredient("maple_sap", 1),
            ), 1))
        )
        `when`(itemService.getItem("maple_sap")).thenReturn(
            item("maple_sap", subtype = "sap", craft = ItemCraft("woodcutting", 40, listOf(
                RecipeIngredient("maple_wood", 15),
            ), 1))
        )
        `when`(bankService.availableQuantity("antidote")).thenReturn(20)

        // when / then
        assertFalse(gatheringService.isRecipeObtainable(character, "antidote", 4))
    }

    @Test
    fun `craftOrGather echoue avant toute collecte quand l'ingredient vient d'un monstre d'evenement absent`() {
        // given — cas snakeskin_armor : l'ogre_eye (2e ingrédient) vient d'un monstre d'événement
        // qui n'est pas apparu sur la carte ; sans garde-fou, l'iron_ore serait récolté pour rien
        `when`(battleService.isMonsterForItemOnMap("ogre_eye")).thenReturn(false)

        // when / then
        assertThrows(UnknownMapException::class.java) {
            gatheringService.craftOrGather(character, "iron_dagger", 1, allowFight = true, shouldTrain = false)
        }
        verifyNoInteractions(gatheringClient)
    }

    @Test
    fun `isRecipeObtainable renvoie false quand l'ingredient vient d'un monstre d'evenement absent`() {
        // given — même situation, via la variante non-jetante utilisée par les jobs
        `when`(battleService.isMonsterForItemOnMap("ogre_eye")).thenReturn(false)

        // when / then
        assertFalse(gatheringService.isRecipeObtainable(character, "iron_dagger", 1))
    }

    @Test
    fun `un ingredient de monstre d'evenement couvert par la banque n'empeche pas la recette`() {
        // given — le monstre d'événement est absent, mais la banque possède déjà l'ogre_eye :
        // le stock banque doit être utilisé comme avant, sans vérifier la présence du monstre
        `when`(battleService.isMonsterForItemOnMap("ogre_eye")).thenReturn(false)
        `when`(bankService.availableQuantity("ogre_eye")).thenReturn(1)

        // when / then
        assertTrue(gatheringService.isRecipeObtainable(character, "iron_dagger", 1))
        verify(battleService, never()).isMonsterForItemOnMap("ogre_eye")
    }

    @Test
    fun `isRecipeObtainable renvoie true quand la recette est entierement realisable`() {
        // given — iron_dagger du setUp : weaponcrafting 10 et mining 5, tous deux à portée

        // when / then
        assertTrue(gatheringService.isRecipeObtainable(character, "iron_dagger", 1))
    }

    private fun item(code: String, subtype: String = "bar", level: Int = 10, craft: ItemCraft? = null) = ItemDetails(
        code = code, name = code, description = "", type = "resource", subtype = subtype,
        level = level, tradeable = true, craft = craft, effects = emptyList(), conditions = emptyList()
    )

    private fun character() = ArtifactsCharacter(
        name = "Renoir", account = "tellenn", level = 20, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = arrayOf(), cooldown = 0, skin = null, task = null,
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0, criticalStrike = 0, speed = 0,
        haste = 0, xp = 0, maxXp = 100, taskType = null, taskTotal = 0, taskProgress = 0,
        miningXp = 0, miningMaxXp = 100, miningLevel = 20, woodcuttingXp = 0, woodcuttingMaxXp = 100,
        woodcuttingLevel = 1, fishingXp = 0, fishingMaxXp = 100, fishingLevel = 1,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 100, weaponcraftingLevel = 20,
        gearcraftingXp = 0, gearcraftingMaxXp = 100, gearcraftingLevel = 1,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 100, jewelrycraftingLevel = 1,
        cookingXp = 0, cookingMaxXp = 100, cookingLevel = 1,
        alchemyXp = 0, alchemyMaxXp = 100, alchemyLevel = 1,
        inventoryMaxItems = 100,
        attackFire = 0, attackEarth = 0, attackWater = 0, attackAir = 0,
        dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0,
        resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
        weaponSlot = null, runeSlot = null, shieldSlot = null, helmetSlot = null,
        bodyArmorSlot = null, legArmorSlot = null, bootsSlot = null,
        ring1Slot = null, ring2Slot = null, amuletSlot = null,
        artifact1Slot = null, artifact2Slot = null, artifact3Slot = null,
        utility1Slot = "", utility1SlotQuantity = 0,
        utility2Slot = "", utility2SlotQuantity = 0,
        bagSlot = null, cooldownExpiration = null,
    )
}
