package com.tellenn.artifacts.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.config.CraftingProperties
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.models.RecipeIngredient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

/**
 * Vérifie la sélection des crafts de leveling contre les **vraies données de l'API** (items.json /
 * monsters.json, récupérés depuis https://api.artifactsmmo.com et figés en fixtures de test).
 *
 * Le test [`génère une table markdown de sélection pour vérification`] produit
 * `docs/superpowers/specs/craft-leveling-selection-table.md` pour relecture humaine.
 */
class CraftLevelingServiceTest {

    private lateinit var itemService: ItemService
    private lateinit var bankService: BankService
    private lateinit var eventService: EventService
    private val props = CraftingProperties()

    // --- chargement des fixtures réelles (une fois) ---
    companion object {
        private val allItems: List<ItemDetails> by lazy { loadItems() }
        private val itemsByCode: Map<String, ItemDetails> by lazy { allItems.associateBy { it.code } }
        private val monsters: List<MonsterData> by lazy { loadMonsters() }

        private fun mapper() = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        private fun loadItems(): List<ItemDetails> =
            mapper().readValue(readFixture("items.json"))

        private fun loadMonsters(): List<MonsterData> =
            mapper().readValue(readFixture("monsters.json"))

        private fun readFixture(name: String): String =
            CraftLevelingServiceTest::class.java.getResource("/fixtures/$name")!!.readText()
    }

    /** ItemService réel branché sur les fixtures (repo + monstres simulés par les vraies données). */
    private fun realItemService(): ItemService {
        val repo = mock(ItemRepository::class.java)
        `when`(repo.getByCode(anyString())).thenAnswer { itemsByCode.getValue(it.getArgument(0)) }
        `when`(repo.findByLevelBetween(anyInt(), anyInt())).thenAnswer {
            val lo = it.getArgument<Int>(0)
            val hi = it.getArgument<Int>(1)
            allItems.filter { item -> item.level in lo..hi }
        }
        val monsterService = mock(MonsterService::class.java)
        `when`(monsterService.findMonsterThatDrop(anyString())).thenAnswer { inv ->
            val code = inv.getArgument<String>(0)
            monsters.filter { m -> m.drops?.any { d -> d.code == code } == true }.minByOrNull { it.level }
        }
        return ItemService(repo, monsterService)
    }

    private fun character(vararg levels: Pair<String, Int>): ArtifactsCharacter {
        val c = mock(ArtifactsCharacter::class.java)
        levels.forEach { (skill, lvl) -> `when`(c.getLevelOf(skill)).thenReturn(lvl) }
        return c
    }

    @BeforeEach
    fun setUp() {
        bankService = mock(BankService::class.java)
        eventService = mock(EventService::class.java)
        `when`(eventService.getAllEventMaterials()).thenReturn(emptyList())
    }

    private fun service(itemSvc: ItemService = realItemService()) =
        CraftLevelingService(itemSvc, bankService, eventService, props)

    // ------------------------------------------------------------------------------------------
    // Données réelles : table de vérification
    // ------------------------------------------------------------------------------------------

    @Test
    fun `génère une table markdown de sélection pour vérification`() {
        // Banque vide de matériaux rares = pire cas → la sélection doit rester sur du « sans rare ».
        `when`(bankService.isInBank(anyString(), anyInt())).thenReturn(false)
        val itemSvc = realItemService()
        val svc = CraftLevelingService(itemSvc, bankService, eventService, props)

        val skills = listOf("jewelrycrafting", "gearcrafting", "weaponcrafting")
        val levels = listOf(20, 25, 30, 35, 40)

        val sb = StringBuilder()
        sb.appendLine("# Craft Leveling — table de vérification de la sélection (données API réelles)")
        sb.appendLine()
        sb.appendLine("_Banque supposée **vide de matériaux rares** (pire cas pour la protection). " +
            "Plancher de réserve = ${props.defaultRareReserve}. Fenêtre d'XP = `niveau-9 .. niveau`._")
        sb.appendLine()
        sb.appendLine("Pour chaque compétence/niveau, l'item **✅ sélectionné** est le moins coûteux " +
            "(`weight`) parmi les recettes **propres** (sans matériau rare/événementiel).")

        for (skill in skills) {
            sb.appendLine().appendLine("## $skill").appendLine()
            for (level in levels) {
                val char = character(skill to level)
                val selected = (svc.selectLevelingCraft(char, skill) as? LevelingChoice.Craft)?.item?.code
                val window = (level - 9)..level
                val candidates = itemSvc.getCrafterItemsBetweenLevel(level - 10, level + 1, listOf(skill))
                    .filter { it.level in window }
                    .map { item -> Triple(item, svc.blockingRequirements(item), itemSvc.getWeightToCraft(item)) }
                    .sortedWith(compareBy({ it.second.isNotEmpty() }, { it.third }))

                sb.appendLine("### Niveau $level (fenêtre ${window.first}–${window.last})  →  sélection : **${selected ?: "AUCUNE (stall)"}**")
                sb.appendLine()
                sb.appendLine("| Lvl | Item | Bloquant (rare/event) | Propre | Weight | Sélectionné |")
                sb.appendLine("|----:|------|------------------------|:------:|-------:|:-----------:|")
                for ((item, blocking, weight) in candidates) {
                    val blockingStr = if (blocking.isEmpty()) "—" else blocking.entries.joinToString(", ") { "${it.key}×${it.value}" }
                    val clean = if (blocking.isEmpty()) "✓" else ""
                    val sel = if (item.code == selected) "✅" else ""
                    sb.appendLine("| ${item.level} | ${item.code} | $blockingStr | $clean | $weight | $sel |")
                }
                sb.appendLine()

                // Garde-fou : banque vide ⇒ on ne doit JAMAIS sélectionner une recette à matériau rare.
                if (selected != null) {
                    assertTrue(
                        svc.blockingRequirements(itemsByCode.getValue(selected)).isEmpty(),
                        "À $skill niveau $level, l'item sélectionné '$selected' ne devrait contenir aucun matériau rare quand la banque est vide"
                    )
                }
            }
        }

        val out = File("docs/superpowers/specs/craft-leveling-selection-table.md")
        out.parentFile.mkdirs()
        out.writeText(sb.toString())
        println(sb)
    }

    @Test
    fun `jewelry 40 sélectionne mithril_ring, seule recette sans rare de la fenêtre`() {
        `when`(bankService.isInBank(anyString(), anyInt())).thenReturn(false)
        val choice = service().selectLevelingCraft(character("jewelrycrafting" to 40), "jewelrycrafting")
        assertInstanceOf(LevelingChoice.Craft::class.java, choice)
        assertEquals("mithril_ring", (choice as LevelingChoice.Craft).item.code)
    }

    @Test
    fun `weaponcrafting niveau 1 ne sélectionne jamais wooden_staff, l'arme de tutoriel limitée`() {
        // wooden_staff (recette weaponcrafting niveau 1) requiert wooden_stick : une arme de départ
        // non craftable distribuée en quantité limitée. On ne doit jamais l'épuiser pour du leveling.
        `when`(bankService.isInBank(anyString(), anyInt())).thenReturn(false)
        val choice = service().selectLevelingCraft(character("weaponcrafting" to 1), "weaponcrafting")
        assertInstanceOf(LevelingChoice.Craft::class.java, choice)
        assertNotEquals("wooden_staff", (choice as LevelingChoice.Craft).item.code)
    }

    @Test
    fun `une recette dépendant de l'arme de tutoriel limitée n'est jamais jouable pour le leveling`() {
        val woodenStaff = ItemDetails(
            code = "wooden_staff", name = "wooden_staff", description = "", type = "weapon", subtype = "",
            level = 1, tradeable = true,
            craft = ItemCraft(
                "weaponcrafting", 1,
                listOf(RecipeIngredient("wooden_stick", 1), RecipeIngredient("ash_wood", 4)), 1
            ),
            effects = emptyList(), conditions = emptyList()
        )
        val svc = service(mockItemServiceReturning("weaponcrafting", woodenStaff))

        val choice = svc.selectLevelingCraft(character("weaponcrafting" to 1), "weaponcrafting")

        assertEquals(LevelingChoice.NoViableRecipe, choice)
    }

    @Test
    fun `blockingRequirements agrège récursivement les matériaux rares à travers les sous-crafts`() {
        // greater_ruby_amulet = ruby×2 + ruby_amulet×1 (qui contient ruby×1 + jasper×2) + astralyte×2
        val req = service().blockingRequirements(itemsByCode.getValue("greater_ruby_amulet"))
        assertEquals(mapOf("ruby" to 3, "jasper_crystal" to 2, "astralyte_crystal" to 2), req)
    }

    // ------------------------------------------------------------------------------------------
    // Logique de réserve / surplus (candidats contrôlés)
    // ------------------------------------------------------------------------------------------

    private fun rareRing(code: String, level: Int, rare: String, qty: Int): ItemDetails =
        ItemDetails(
            code = code, name = code, description = "", type = "ring", subtype = "",
            level = level, tradeable = true,
            craft = ItemCraft("jewelrycrafting", level, listOf(RecipeIngredient(rare, qty)), 1),
            effects = emptyList(), conditions = emptyList()
        )

    /** Stub `getCrafterItemsBetweenLevel` en renvoyant les candidats selon la compétence demandée. */
    private fun stubCandidatesBySkill(svc: ItemService, bySkill: Map<String, List<ItemDetails>>) {
        `when`(svc.getCrafterItemsBetweenLevel(anyInt(), anyInt(), anyList())).thenAnswer { inv ->
            bySkill[inv.getArgument<List<String>>(2).firstOrNull()] ?: emptyList<ItemDetails>()
        }
    }

    private fun mockItemServiceReturning(skill: String, vararg candidates: ItemDetails): ItemService {
        val svc = mock(ItemService::class.java)
        stubCandidatesBySkill(svc, mapOf(skill to candidates.toList()))
        return svc
    }

    @Test
    fun `dépense le surplus quand stock moins quantité égale le plancher`() {
        val ring = rareRing("surplus_ring", 25, "ruby", 1) // plancher ruby = 10
        val svc = service(mockItemServiceReturning("jewelrycrafting", ring))
        `when`(bankService.isInBank("ruby", 11)).thenReturn(true) // 11 - 1 = 10 = plancher

        val choice = svc.selectLevelingCraft(character("jewelrycrafting" to 25), "jewelrycrafting")
        assertInstanceOf(LevelingChoice.Craft::class.java, choice)
        assertEquals("surplus_ring", (choice as LevelingChoice.Craft).item.code)
    }

    @Test
    fun `refuse de crafter quand le stock est un sous le plancher`() {
        val ring = rareRing("surplus_ring", 25, "ruby", 1)
        val svc = service(mockItemServiceReturning("jewelrycrafting", ring))
        `when`(bankService.isInBank("ruby", 11)).thenReturn(false) // seulement 10 en banque ⇒ 10 - 1 < plancher

        val choice = svc.selectLevelingCraft(character("jewelrycrafting" to 25), "jewelrycrafting")
        assertEquals(LevelingChoice.NoViableRecipe, choice)
    }

    @Test
    fun `selectSkillToLevel saute une compétence bloquée et renvoie la suivante jouable`() {
        val cleanRing = ItemDetails(
            code = "clean_ring", name = "clean_ring", description = "", type = "ring", subtype = "",
            level = 20, tradeable = true,
            craft = ItemCraft("gearcrafting", 20, listOf(RecipeIngredient("iron_bar", 5)), 1),
            effects = emptyList(), conditions = emptyList()
        )
        val svc = mock(ItemService::class.java)
        // weaponcrafting (niv. 10, le plus bas) et jewelrycrafting : aucun candidat ⇒ stall ;
        // gearcrafting (niv. 20) : une recette propre.
        stubCandidatesBySkill(svc, mapOf("gearcrafting" to listOf(cleanRing)))
        // iron_bar n'est pas rare ⇒ accumulate descend dedans : craft null pour s'arrêter
        `when`(svc.getItem("iron_bar")).thenReturn(
            ItemDetails("iron_bar", "iron_bar", "", "resource", "bar", 10, true, false, null, emptyList(), emptyList())
        )

        val skill = service(svc).selectSkillToLevel(
            character("weaponcrafting" to 10, "gearcrafting" to 20, "jewelrycrafting" to 30),
            listOf("weaponcrafting", "gearcrafting", "jewelrycrafting")
        )
        assertEquals("gearcrafting", skill)
    }

    @Test
    fun `selectSkillToLevel renvoie null quand toutes les compétences sont bloquées`() {
        val svc = mock(ItemService::class.java)
        stubCandidatesBySkill(svc, emptyMap())
        val skill = service(svc).selectSkillToLevel(
            character("weaponcrafting" to 10, "gearcrafting" to 20, "jewelrycrafting" to 30),
            listOf("weaponcrafting", "gearcrafting", "jewelrycrafting")
        )
        assertEquals(null, skill)
    }
}
