package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.repositories.CraftedItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.RecipeIngredient
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.services.AchievementService
import com.tellenn.artifacts.services.BankService
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant

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

class CrafterJobCleanUpBankTest {

    private lateinit var movementService: MovementService
    private lateinit var bankService: BankService
    private lateinit var characterService: CharacterService
    private lateinit var gatheringService: GatheringService
    private lateinit var job: CrafterJob

    @BeforeEach
    fun setUp() {
        movementService = mock(MovementService::class.java)
        bankService = mock(BankService::class.java)
        characterService = mock(CharacterService::class.java)
        gatheringService = mock(GatheringService::class.java)

        job = CrafterJob(
            mapService = mock(MapService::class.java),
            movementService = movementService,
            bankService = bankService,
            characterService = characterService,
            accountClient = mock(AccountClient::class.java),
            taskService = mock(TaskService::class.java),
            itemService = mock(ItemService::class.java),
            craftedItemRepository = mock(CraftedItemRepository::class.java),
            gatheringService = gatheringService,
            eventService = mock(EventService::class.java),
            monsterService = mock(MonsterService::class.java),
            raidService = mock(RaidService::class.java),
            achievementService = mock(AchievementService::class.java),
            uniqueArtifactService = mock(UniqueArtifactService::class.java),
            contextService = mock(CharacterContextService::class.java),
            craftLevelingService = mock(CraftLevelingService::class.java),
        )
        job.character = character()
    }

    @Test
    fun `wooden_stick is destroyed, craftable equipment is recycled in full, non-craftable items are untouched`() {
        // given — the bank equipment under the crafter threshold. wooden_stick is the tutorial
        // weapon (destroyed, never recycled); wooden_staff is craftable (recycled in full);
        // the rest have no craft recipe, mirroring the itemDetails data, so they stay untouched.
        val bankItems = listOf(
            equipment("wooden_stick", "weapon", level = 1, craft = recipe("weaponcrafting")),
            equipment("wooden_staff", "weapon", level = 1, craft = recipe("weaponcrafting")),
            equipment("forest_ring", "ring", level = 10, craft = null),
            equipment("highwayman_dagger", "weapon", level = 15, craft = null),
            equipment("wolf_ears", "helmet", level = 15, craft = null),
            equipment("old_boots", "boots", level = 20, craft = null),
        )
        `when`(bankService.getAllEquipmentsUnderLevel(anyInt())).thenReturn(bankItems)
        `when`(movementService.moveToBank(anyObject(), anyBoolean())).thenReturn(job.character)
        `when`(bankService.emptyInventory(anyObject())).thenReturn(job.character)
        `when`(bankService.withdrawOne(anyString(), anyInt(), anyObject())).thenReturn(job.character)
        // 3 wooden_staff in the bank → the whole stock must be recycled (here in a single step).
        `when`(bankService.getOne("wooden_staff")).thenReturn(SimpleItem("wooden_staff", 3))

        val destroyedCodes = mutableListOf<String>()
        `when`(characterService.destroyAllOfOne(anyObject(), anyString())).thenAnswer { invocation ->
            destroyedCodes.add(invocation.getArgument(1) as String)
            job.character
        }
        val recycled = mutableListOf<Pair<String, Int>>()
        `when`(gatheringService.recycle(anyObject(), anyObject(), anyInt(), anyBoolean())).thenAnswer { invocation ->
            recycled.add((invocation.getArgument(1) as ItemDetails).code to (invocation.getArgument(2) as Int))
            job.character
        }

        // when
        job.cleanUpBank()

        // then — wooden_stick destroyed, wooden_staff recycled in full (qty 3), nothing else
        assertEquals(listOf("wooden_stick"), destroyedCodes)
        assertEquals(listOf("wooden_staff" to 3), recycled)
    }

    @Test
    fun `un gros stock est recycle par etapes qui ne debordent jamais l'inventaire`() {
        // given — 40 pièces d'un équipement dont la recette rend jusqu'à 6 matériaux par pièce.
        // Tout retirer d'un coup débordait l'inventaire de 100 (497 en boucle). Capacité utile
        // 100 - 5 = 95, footprint 6 → chunk de 15 : 40 se recycle en 15 + 15 + 10.
        val heavyRecipe = ItemCraft(
            skill = "gearcrafting", level = 1,
            items = listOf(RecipeIngredient(code = "iron", quantity = 6)), quantity = 1,
        )
        `when`(bankService.getAllEquipmentsUnderLevel(anyInt()))
            .thenReturn(listOf(equipment("iron_helmet", "helmet", level = 1, craft = heavyRecipe)))
        `when`(movementService.moveToBank(anyObject(), anyBoolean())).thenReturn(job.character)
        `when`(bankService.emptyInventory(anyObject())).thenReturn(job.character)
        `when`(bankService.getOne("iron_helmet")).thenReturn(SimpleItem("iron_helmet", 40))

        val withdrawnSteps = mutableListOf<Int>()
        `when`(bankService.withdrawOne(anyString(), anyInt(), anyObject())).thenAnswer { invocation ->
            withdrawnSteps.add(invocation.getArgument(1) as Int)
            job.character
        }
        val recycledSteps = mutableListOf<Int>()
        `when`(gatheringService.recycle(anyObject(), anyObject(), anyInt(), anyBoolean())).thenAnswer { invocation ->
            recycledSteps.add(invocation.getArgument(2) as Int)
            job.character
        }

        // when
        job.cleanUpBank()

        // then — recyclé en étapes bornées par le chunk, somme égale au stock, sans jamais retirer plus
        assertEquals(listOf(15, 15, 10), recycledSteps)
        assertEquals(40, recycledSteps.sum())
        assertEquals(withdrawnSteps, recycledSteps)
        assertTrue(withdrawnSteps.all { it <= 15 })
    }

    private fun recipe(skill: String) = ItemCraft(skill = skill, level = 1, items = emptyList(), quantity = 1)

    private fun equipment(code: String, type: String, level: Int, craft: ItemCraft?) = BankItemDocument(
        code = code, name = code, description = "", type = type, subtype = "", level = level,
        tradeable = true, recyclable = true, effects = null, craft = craft, conditions = null, quantity = 1,
    )

    private fun character() = ArtifactsCharacter(
        name = "Renoir", account = "tellenn", level = 30, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = arrayOf(), cooldown = 0, skin = null, task = null,
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0, criticalStrike = 0, speed = 0,
        haste = 0, xp = 0, maxXp = 100, taskType = null, taskTotal = 0, taskProgress = 0,
        miningXp = 0, miningMaxXp = 100, miningLevel = 1, woodcuttingXp = 0, woodcuttingMaxXp = 100,
        woodcuttingLevel = 1, fishingXp = 0, fishingMaxXp = 100, fishingLevel = 1,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 100, weaponcraftingLevel = 30,
        gearcraftingXp = 0, gearcraftingMaxXp = 100, gearcraftingLevel = 30,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 100, jewelrycraftingLevel = 30,
        cookingXp = 0, cookingMaxXp = 100, cookingLevel = 1, alchemyXp = 0, alchemyMaxXp = 100, alchemyLevel = 1,
        inventoryMaxItems = 100, attackFire = 0, attackEarth = 0, attackWater = 0, attackAir = 0,
        dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0, resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
        weaponSlot = null, runeSlot = null, shieldSlot = null, helmetSlot = null, bodyArmorSlot = null,
        legArmorSlot = null, bootsSlot = null, ring1Slot = null, ring2Slot = null, amuletSlot = null,
        artifact1Slot = null, artifact2Slot = null, artifact3Slot = null,
        utility1Slot = "", utility1SlotQuantity = 0, utility2Slot = "", utility2SlotQuantity = 0,
        bagSlot = null, cooldownExpiration = Instant.now(),
    )
}
