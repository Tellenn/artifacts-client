package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.db.repositories.CraftedItemRepository
import com.tellenn.artifacts.exceptions.CharacterSkillTooLow
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.models.RecipeIngredient
import com.tellenn.artifacts.services.AchievementService
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.BossFightService
import com.tellenn.artifacts.services.CharacterContextService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.CraftLevelingService
import com.tellenn.artifacts.services.EventService
import com.tellenn.artifacts.services.GatheringService
import com.tellenn.artifacts.services.ItemService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.MonsterService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.RaidService
import com.tellenn.artifacts.services.TaskService
import com.tellenn.artifacts.services.UniqueArtifactService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.any
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

/**
 * Helpers null-safe pour les matchers Mockito sur des paramètres Kotlin non-nullables
 * (même pattern que [CrafterJobCleanUpBankTest]).
 */
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

/** `eq()` version null-safe : Mockito renvoie `null`, rejeté par les paramètres String non-nullables. */
private fun <T> eqObject(value: T): T {
    eq(value)
    return value
}

/**
 * Couvre [CrafterJob.craftOneItemForBank] : un seul item crafté par appel (l'appelant re-vérifie
 * la couverture du batch de leveling entre chaque), priorité aux crafts instantanés depuis le
 * stock banque, et repli sur le candidat suivant quand un craft échoue.
 */
class CrafterJobCraftForBankTest {

    private lateinit var movementService: MovementService
    private lateinit var bankService: BankService
    private lateinit var gatheringService: GatheringService
    private lateinit var itemService: ItemService
    private lateinit var craftedItemRepository: CraftedItemRepository
    private lateinit var accountClient: AccountClient
    private lateinit var monsterService: MonsterService
    private lateinit var bossFightService: BossFightService
    private lateinit var job: CrafterJob

    @BeforeEach
    fun setUp() {
        movementService = mock(MovementService::class.java)
        bankService = mock(BankService::class.java)
        gatheringService = mock(GatheringService::class.java)
        itemService = mock(ItemService::class.java)
        craftedItemRepository = mock(CraftedItemRepository::class.java)
        accountClient = mock(AccountClient::class.java)
        monsterService = mock(MonsterService::class.java)
        bossFightService = mock(BossFightService::class.java)

        job = CrafterJob(
            mapService = mock(MapService::class.java),
            movementService = movementService,
            bankService = bankService,
            characterService = mock(CharacterService::class.java),
            accountClient = accountClient,
            taskService = mock(TaskService::class.java),
            itemService = itemService,
            craftedItemRepository = craftedItemRepository,
            gatheringService = gatheringService,
            eventService = mock(EventService::class.java),
            monsterService = monsterService,
            raidService = mock(RaidService::class.java),
            achievementService = mock(AchievementService::class.java),
            uniqueArtifactService = mock(UniqueArtifactService::class.java),
            contextService = mock(CharacterContextService::class.java),
            craftLevelingService = mock(CraftLevelingService::class.java),
            bossFightService = bossFightService,
        )
        job.character = character()

        `when`(accountClient.getCharacter(anyString())).thenReturn(ArtifactsResponseBody(job.character))
        `when`(craftedItemRepository.findById(anyString())).thenReturn(Optional.empty())
        `when`(craftedItemRepository.save(anyObject())).thenAnswer { it.getArgument(0) }
        `when`(movementService.moveToBank(anyObject(), anyBoolean())).thenReturn(job.character)
        `when`(bankService.emptyInventory(anyObject())).thenReturn(job.character)
        `when`(bankService.getAllEquipmentsUnderLevel(anyInt())).thenReturn(emptyList())
        `when`(
            gatheringService.craftOrGather(anyObject(), anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean())
        ).thenReturn(job.character)
    }

    private fun character(): ArtifactsCharacter {
        val c = mock(ArtifactsCharacter::class.java)
        `when`(c.name).thenReturn("Renoir")
        `when`(c.getLevelOf(anyString())).thenReturn(5)
        return c
    }

    private fun craftable(code: String, level: Int = 5): ItemDetails =
        ItemDetails(
            code = code, name = code, description = "", type = "weapon", subtype = "",
            level = level, tradeable = true,
            craft = ItemCraft("weaponcrafting", level, listOf(RecipeIngredient("iron_bar", 2)), 1),
            effects = emptyList(), conditions = emptyList()
        )

    private fun bossCraftable(code: String, ingredientCode: String, level: Int = 5): ItemDetails =
        ItemDetails(
            code = code, name = code, description = "", type = "weapon", subtype = "",
            level = level, tradeable = true,
            craft = ItemCraft("weaponcrafting", level, listOf(RecipeIngredient(ingredientCode, 1)), 1),
            effects = emptyList(), conditions = emptyList()
        )

    private fun bossMonster(code: String) = MonsterData(
        name = code, code = code, level = 40, hp = 5000,
        attackFire = 0, attackEarth = 0, attackWater = 0, attackAir = 0,
        defenseFire = 0, defenseEarth = 0, defenseWater = 0, defenseAir = 0,
        criticalStrike = 0, effects = emptyList(), minGold = 0, maxGold = 0,
        drops = null, initiative = 0, type = "boss",
    )

    private fun stubCandidates(vararg items: ItemDetails) {
        `when`(itemService.getCrafterItemsBetweenLevel(anyInt(), anyInt(), anyList())).thenAnswer { inv ->
            if (inv.getArgument<List<String>>(2).firstOrNull() == "weaponcrafting") items.toList()
            else emptyList<ItemDetails>()
        }
    }

    @Test
    fun `craft un seul item instantane depuis le stock banque et s'arrete`() {
        // given : deux candidats craftables depuis la banque
        stubCandidates(craftable("copper_dagger"), craftable("iron_dagger"))
        `when`(bankService.canCraftFromBank(anyObject(), anyInt())).thenReturn(true)

        // when
        val crafted = job.craftOneItemForBank()

        // then : un seul craft (le premier), sans combat
        assertTrue(crafted)
        verify(gatheringService, times(1))
            .craftOrGather(anyObject(), eqObject("copper_dagger"), eq(1), anyInt(), eq(false), anyBoolean())
    }

    @Test
    fun `sans stock banque, collecte et craft le premier candidat puis s'arrete`() {
        // given : rien de craftable instantanément
        stubCandidates(craftable("copper_dagger"), craftable("iron_dagger"))
        `when`(bankService.canCraftFromBank(anyObject(), anyInt())).thenReturn(false)

        // when
        val crafted = job.craftOneItemForBank()

        // then : un seul gather+craft, combat autorisé
        assertTrue(crafted)
        verify(gatheringService, times(1))
            .craftOrGather(anyObject(), eqObject("copper_dagger"), eq(1), anyInt(), eq(true), eq(false))
        verify(gatheringService, never())
            .craftOrGather(anyObject(), eqObject("iron_dagger"), anyInt(), anyInt(), anyBoolean(), anyBoolean())
    }

    @Test
    fun `renvoie faux quand aucun item n'est a crafter pour la banque`() {
        // given : aucun candidat
        stubCandidates()

        // when - then
        assertFalse(job.craftOneItemForBank())
        verify(gatheringService, never())
            .craftOrGather(anyObject(), anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean())
    }

    @Test
    fun `item a ingredient boss hors banque est tente quand la party boss est libre`() {
        // given : un candidat dont l'ingrédient vient d'un boss, absent de la banque, party libre
        stubCandidates(bossCraftable("obsidian_sword", "bloodshard"))
        `when`(bankService.canCraftFromBank(anyObject(), anyInt())).thenReturn(false)
        `when`(bankService.isInBank("bloodshard", 1)).thenReturn(false)
        `when`(monsterService.findMonsterThatDrop("bloodshard")).thenReturn(bossMonster("demon_king"))
        `when`(bossFightService.isPartyAvailable()).thenReturn(true)

        // when
        val crafted = job.craftOneItemForBank()

        // then : le craft est tenté avec combat autorisé (le chemin boss prend le relais)
        assertTrue(crafted)
        verify(gatheringService, times(1))
            .craftOrGather(anyObject(), eqObject("obsidian_sword"), eq(1), anyInt(), eq(true), eq(false))
    }

    @Test
    fun `item a ingredient boss hors banque est exclu quand la party boss est occupee`() {
        // given : même candidat boss, mais la party n'est pas disponible
        stubCandidates(bossCraftable("obsidian_sword", "bloodshard"))
        `when`(bankService.canCraftFromBank(anyObject(), anyInt())).thenReturn(false)
        `when`(bankService.isInBank("bloodshard", 1)).thenReturn(false)
        `when`(monsterService.findMonsterThatDrop("bloodshard")).thenReturn(bossMonster("demon_king"))
        `when`(bossFightService.isPartyAvailable()).thenReturn(false)

        // when - then : aucun craft tenté
        assertFalse(job.craftOneItemForBank())
        verify(gatheringService, never())
            .craftOrGather(anyObject(), anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean())
    }

    @Test
    fun `item a ingredient boss deja en banque reste craftable meme party occupee`() {
        // given : l'ingrédient boss est en stock, la party est occupée
        stubCandidates(bossCraftable("obsidian_sword", "bloodshard"))
        `when`(bankService.canCraftFromBank(anyObject(), anyInt())).thenReturn(false)
        `when`(bankService.isInBank("bloodshard", 1)).thenReturn(true)
        `when`(monsterService.findMonsterThatDrop("bloodshard")).thenReturn(bossMonster("demon_king"))
        `when`(bossFightService.isPartyAvailable()).thenReturn(false)

        // when
        val crafted = job.craftOneItemForBank()

        // then : le stock banque suffit, pas besoin de la party
        assertTrue(crafted)
        verify(gatheringService, times(1))
            .craftOrGather(anyObject(), eqObject("obsidian_sword"), eq(1), anyInt(), eq(true), eq(false))
    }

    @Test
    fun `passe au candidat suivant quand le premier echoue sur une competence trop basse`() {
        // given : le premier gather+craft échoue (compétence trop basse), le second réussit
        stubCandidates(craftable("copper_dagger"), craftable("iron_dagger"))
        `when`(bankService.canCraftFromBank(anyObject(), anyInt())).thenReturn(false)
        `when`(
            gatheringService.craftOrGather(anyObject(), eqObject("copper_dagger"), anyInt(), anyInt(), anyBoolean(), anyBoolean())
        ).thenThrow(CharacterSkillTooLow(skill = "weaponcrafting", level = 10))

        // when
        val crafted = job.craftOneItemForBank()

        // then : le second candidat est crafté
        assertTrue(crafted)
        verify(gatheringService, times(1))
            .craftOrGather(anyObject(), eqObject("iron_dagger"), eq(1), anyInt(), eq(true), eq(false))
    }
}
