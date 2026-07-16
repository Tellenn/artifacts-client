package com.tellenn.artifacts.services

import com.tellenn.artifacts.config.CraftingProperties
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import org.springframework.stereotype.Service
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Résultat de la sélection d'un craft de leveling pour une compétence donnée.
 */
sealed class LevelingChoice {
    /**
     * Un craft est disponible sans entamer la réserve protégée.
     *
     * [batchSize] indique combien d'exemplaires crafter d'affilée : la pleine taille de batch pour
     * une recette « propre » (aucun matériau protégé), mais `1` pour une recette qui puise dans le
     * surplus rare — l'appelant doit alors re-sélectionner avant chaque craft pour rester au-dessus
     * du plancher de réserve.
     */
    data class Craft(val item: ItemDetails, val batchSize: Int) : LevelingChoice()

    /** Aucune recette « sans matériau rare » ni couverte par un surplus : il faut faire autre chose. */
    data object NoViableRecipe : LevelingChoice()
}

/**
 * Choisit la recette à crafter pour monter une compétence de craft tout en protégeant les
 * matériaux rares.
 *
 * Règles :
 * 1. On ne considère que les recettes dont l'XP est non nulle (item dans la fenêtre
 *    `niveau-9 .. niveau`, le malus d'XP étant total au-delà de 9 niveaux d'écart).
 * 2. On préfère les recettes **sans aucun matériau rare/événementiel** dans tout leur arbre de craft.
 * 3. À défaut, on autorise une recette rare uniquement si **chaque** matériau rare requis dispose
 *    d'un surplus au-dessus de son plancher de réserve (`stockBanque - quantité >= plancher`).
 * 4. Sinon, [LevelingChoice.NoViableRecipe] : le personnage doit faire autre chose plutôt que
 *    d'entamer la réserve.
 *
 * L'appelant re-sélectionne avant chaque craft : comme un craft consomme exactement la quantité
 * requise, chaque craft laisse la banque au-dessus du plancher, et le surplus épuisé bascule
 * naturellement vers [LevelingChoice.NoViableRecipe].
 */
@Service
class CraftLevelingService(
    private val itemService: ItemService,
    private val bankService: BankService,
    private val eventService: EventService,
    private val props: CraftingProperties,
) {
    /**
     * Quantité totale de chaque matériau protégé (rare ou événementiel) nécessaire pour crafter
     * [item], agrégée récursivement à travers les sous-crafts. Vide ⇒ recette « propre ».
     */
    fun blockingRequirements(item: ItemDetails): Map<String, Int> =
        blockingRequirements(item, eventMaterials())

    fun selectLevelingCraft(character: ArtifactsCharacter, skill: String): LevelingChoice {
        val skillLevel = character.getLevelOf(skill)
        val candidates = windowCandidates(skill, skillLevel)
        if (candidates.isEmpty()) return LevelingChoice.NoViableRecipe

        val events = eventMaterials()

        val clean = candidates.filter { blockingRequirements(it, events).isEmpty() }
        if (clean.isNotEmpty()) {
            val item = cheapest(clean)
            return LevelingChoice.Craft(item, craftsToReachNextMilestone(character, skill, item))
        }

        // Recette « coverable » : on puise dans le surplus rare, donc un seul craft à la fois pour
        // que l'appelant re-sélectionne et garde chaque matériau protégé au-dessus de son plancher.
        val coverable = candidates.filter { isCoverable(blockingRequirements(it, events)) }
        if (coverable.isNotEmpty()) return LevelingChoice.Craft(cheapest(coverable), 1)

        return LevelingChoice.NoViableRecipe
    }

    /**
     * XP de craft gagnée pour **un** exemplaire de [item] en entraînant [skill], selon la formule de
     * l'API (palier de base par niveau de ressource, malus de niveau en falaise à 10 niveaux d'écart,
     * bonus de sagesse). Destinée à terme à dimensionner le batch de leveling.
     */
    fun xpPerCraft(character: ArtifactsCharacter, item: ItemDetails, skill: String): Int =
        craftXp(itemLevel = item.level, playerLevel = character.getLevelOf(skill), wisdom = character.wisdom, skill = skill)

    /** XP nécessaire pour passer de [level] à [level]+1 (taille de la barre d'XP), `0` au niveau max. */
    fun xpToLevelUp(level: Int): Int = XP_REQUIRED_PER_LEVEL[level] ?: 0

    /**
     * XP qu'il reste à gagner pour monter de [levels] niveaux, en partant du niveau [currentLevel]
     * avec [currentXp] points déjà acquis dans le niveau courant : on termine d'abord la barre
     * courante, puis on additionne les barres pleines des niveaux suivants.
     */
    fun xpToGainLevels(currentLevel: Int, currentXp: Int, levels: Int = 1): Int {
        require(levels >= 1) { "Le nombre de niveaux à gagner doit être >= 1, reçu $levels" }
        var total = (xpToLevelUp(currentLevel) - currentXp).coerceAtLeast(0)
        for (level in currentLevel + 1 until currentLevel + levels) {
            total += xpToLevelUp(level)
        }
        return total
    }

    /** La plus basse des [skills] qui a une recette jouable, sinon `null` (⇒ faire autre chose). */
    fun selectSkillToLevel(character: ArtifactsCharacter, skills: List<String>): String? =
        skills.sortedBy { character.getLevelOf(it) / 5}
            .firstOrNull { selectLevelingCraft(character, it) is LevelingChoice.Craft }

    /**
     * Vrai si la banque couvre déjà (réservations déduites) la totalité des ingrédients directs
     * de [item] pour [batchSize] crafts : le crafter peut assembler immédiatement, sans phase de
     * collecte. Un item non craftable n'est jamais prêt.
     */
    fun isLevelingBatchReady(item: ItemDetails, batchSize: Int): Boolean =
        item.craft?.items?.all { ingredient ->
            bankService.availableQuantity(ingredient.code) >= ingredient.quantity * batchSize
        } ?: false

    /**
     * Nombre de crafts de [item] nécessaires pour amener [skill] jusqu'au prochain palier de
     * [MILESTONE_STEP] niveaux (5, 10, 15, …), à partir du niveau et de l'XP courants du personnage.
     *
     * `crafts = ceil(XP jusqu'au palier / XP par craft)`, au minimum 1. Estimation faite au niveau
     * courant : l'appelant re-sélectionne après chaque batch, donc un éventuel sur/sous-dimensionnement
     * se corrige naturellement à l'itération suivante.
     */
    private fun craftsToReachNextMilestone(character: ArtifactsCharacter, skill: String, item: ItemDetails): Int {
        val currentLevel = character.getLevelOf(skill)
        val levelsToGain = nextMilestone(currentLevel) - currentLevel
        if (levelsToGain <= 0) return 1
        val xpNeeded = xpToGainLevels(currentLevel, character.getXpOf(skill), levelsToGain)
        val xpPerCraft = xpPerCraft(character, item, skill)
        if (xpNeeded <= 0 || xpPerCraft <= 0) return 1
        return ceil(xpNeeded.toDouble() / xpPerCraft).toInt().coerceAtLeast(1)
    }

    /** Prochain multiple de [MILESTONE_STEP] strictement supérieur à [level]. */
    private fun nextMilestone(level: Int): Int = (level / MILESTONE_STEP + 1) * MILESTONE_STEP

    private fun cheapest(items: List<ItemDetails>): ItemDetails =
        items.minBy { itemService.getWeightToCraft(it) }

    private fun windowCandidates(skill: String, skillLevel: Int): List<ItemDetails> {
        val lowestXpLevel = skillLevel - XP_WINDOW
        return itemService
            .getCrafterItemsBetweenLevel(lowestXpLevel - 1, skillLevel + 1, listOf(skill))
            .filter { it.level in lowestXpLevel..skillLevel }
            .filterNot { requiresLimitedStarterWeapon(it) }
    }

    /**
     * Vrai si la recette dépend, directement ou via un sous-craft, de [LIMITED_STARTER_WEAPON].
     *
     * Cette arme de départ (ex. `wooden_stick` requise par `wooden_staff`) n'est ni craftable ni
     * récoltable : elle est distribuée en quantité limitée en début de partie (une par personnage).
     * La consommer pour monter une compétence épuiserait définitivement la réserve — et déclencherait
     * en aval le déséquipement de l'arme réelle du crafter pour la « récolter ». On exclut donc toute
     * recette qui en dépend de la sélection de leveling.
     */
    private fun requiresLimitedStarterWeapon(item: ItemDetails): Boolean =
        item.craft?.items?.any { ingredient ->
            ingredient.code == LIMITED_STARTER_WEAPON ||
                // Les matériaux rares sont des feuilles protégées : jamais l'arme de départ ni
                // composées d'elle — inutile (et impossible) de descendre dedans.
                (!props.isRare(ingredient.code) &&
                    requiresLimitedStarterWeapon(itemService.getItem(ingredient.code)))
        } ?: false

    private fun eventMaterials(): Set<String> = eventService.getAllEventMaterials().toSet()

    private fun blockingRequirements(item: ItemDetails, events: Set<String>): Map<String, Int> {
        val acc = HashMap<String, Int>()
        item.craft?.items?.forEach { accumulate(it.code, it.quantity, events, acc) }
        return acc
    }

    private fun accumulate(code: String, quantity: Int, events: Set<String>, acc: HashMap<String, Int>) {
        if (props.isRare(code) || code in events) {
            acc[code] = (acc[code] ?: 0) + quantity
            return // un matériau protégé est consommé tel quel — on ne le craft jamais pour du leveling
        }
        val craft = itemService.getItem(code).craft ?: return
        craft.items.forEach { accumulate(it.code, it.quantity * quantity, events, acc) }
    }

    private fun isCoverable(requirements: Map<String, Int>): Boolean =
        requirements.all { (code, quantity) ->
            val floor = if (props.isRare(code)) props.reserveFloor(code) else 0
            bankService.isInBank(code, quantity + floor)
        }

    companion object {
        /** Crafter un item 10 niveaux ou plus sous le personnage donne 0 XP : fenêtre = `niveau-9 .. niveau`. */
        private const val XP_WINDOW = 9

        /** Pas des paliers de niveau visés par le batch de leveling (5, 10, 15, …). */
        private const val MILESTONE_STEP = 5

        /** Arme de départ non craftable/récoltable, distribuée en quantité limitée (une par personnage). */
        private const val LIMITED_STARTER_WEAPON = "wooden_stick"
    }
}

/**
 * XP nécessaire pour passer du niveau (clé) au suivant — taille de la barre d'XP à ce niveau.
 * Le niveau 50 est le maximum : aucune entrée (⇒ 0 via [CraftLevelingService.xpToLevelUp]).
 */
private val XP_REQUIRED_PER_LEVEL: Map<Int, Int> = mapOf(
    1 to 150, 2 to 250, 3 to 350, 4 to 450, 5 to 700,
    6 to 950, 7 to 1_200, 8 to 1_450, 9 to 1_700, 10 to 2_100,
    11 to 2_500, 12 to 2_900, 13 to 3_300, 14 to 3_700, 15 to 4_400,
    16 to 5_100, 17 to 5_800, 18 to 6_500, 19 to 7_200, 20 to 8_200,
    21 to 9_200, 22 to 10_200, 23 to 11_200, 24 to 12_200, 25 to 13_400,
    26 to 14_600, 27 to 15_800, 28 to 17_000, 29 to 18_200, 30 to 19_700,
    31 to 21_200, 32 to 22_700, 33 to 24_200, 34 to 25_700, 35 to 27_500,
    36 to 29_300, 37 to 31_100, 38 to 32_900, 39 to 34_700, 40 to 36_500,
    41 to 38_600, 42 to 40_700, 43 to 42_800, 44 to 44_900, 45 to 47_000,
    46 to 48_800, 47 to 50_600, 48 to 52_400, 49 to 54_200,
)

/** Écart de niveau (joueur au-dessus de l'item) à partir duquel le craft ne donne plus d'XP. */
private const val XP_LEVEL_PENALTY_CUTOFF = 10

/** Gain d'XP par point de sagesse (0,1 %). */
private const val XP_WISDOM_PER_POINT = 0.001

/**
 * XP de craft pour un exemplaire, selon la formule de l'API :
 * `round((xpBase + (itemLevel / playerLevel) × coefficient) × skillMultiplier × levelPenalty × wisdomBonus)`.
 *
 * - `xpBase` et `coefficient` dépendent du niveau de l'item ([craftXpBaseAndCoefficient]).
 * - `skillMultiplier` réduit l'XP des compétences de récolte / cuisine ([skillMultiplier]).
 * - `levelPenalty` est en falaise : `1.0` tant que le joueur est à moins de [XP_LEVEL_PENALTY_CUTOFF]
 *   niveaux au-dessus de l'item, `0.0` au-delà (égal ⇒ aucun XP).
 * - `wisdomBonus = 1 + wisdom × 0.001`.
 */
internal fun craftXp(itemLevel: Int, playerLevel: Int, wisdom: Int, skill: String): Int {
    if (playerLevel - itemLevel >= XP_LEVEL_PENALTY_CUTOFF) return 0
    val (base, coefficient) = craftXpBaseAndCoefficient(itemLevel)
    val ratioBonus = itemLevel.toDouble() / playerLevel.coerceAtLeast(1) * coefficient
    val wisdomBonus = 1 + wisdom * XP_WISDOM_PER_POINT
    return ((base + ratioBonus) * skillMultiplier(skill) * wisdomBonus).roundToInt()
}

/** Palier (XP de base, coefficient) selon le niveau de l'item crafté. */
private fun craftXpBaseAndCoefficient(itemLevel: Int): Pair<Int, Int> = when {
    itemLevel < 5 -> 50 to 25
    itemLevel < 10 -> 100 to 30
    itemLevel < 15 -> 200 to 35
    itemLevel < 20 -> 325 to 40
    itemLevel < 25 -> 450 to 45
    itemLevel < 30 -> 550 to 50
    itemLevel < 35 -> 650 to 55
    itemLevel < 40 -> 750 to 60
    itemLevel < 45 -> 850 to 65
    else -> 1000 to 70
}

/** Multiplicateur d'XP par compétence : récolte fortement réduite, cuisine à moitié, crafts à plein. */
private fun skillMultiplier(skill: String): Double = when (skill) {
    "mining", "woodcutting", "fishing" -> 0.1
    "cooking" -> 0.5
    else -> 1.0
}
