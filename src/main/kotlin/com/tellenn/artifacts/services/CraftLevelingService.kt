package com.tellenn.artifacts.services

import com.tellenn.artifacts.config.CraftingProperties
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import org.springframework.stereotype.Service

/**
 * Résultat de la sélection d'un craft de leveling pour une compétence donnée.
 */
sealed class LevelingChoice {
    /** Un craft est disponible sans entamer la réserve protégée. */
    data class Craft(val item: ItemDetails) : LevelingChoice()

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
        if (clean.isNotEmpty()) return LevelingChoice.Craft(cheapest(clean))

        val coverable = candidates.filter { isCoverable(blockingRequirements(it, events)) }
        if (coverable.isNotEmpty()) return LevelingChoice.Craft(cheapest(coverable))

        return LevelingChoice.NoViableRecipe
    }

    /** La plus basse des [skills] qui a une recette jouable, sinon `null` (⇒ faire autre chose). */
    fun selectSkillToLevel(character: ArtifactsCharacter, skills: List<String>): String? =
        skills.sortedBy { character.getLevelOf(it) }
            .firstOrNull { selectLevelingCraft(character, it) is LevelingChoice.Craft }

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

        /** Arme de départ non craftable/récoltable, distribuée en quantité limitée (une par personnage). */
        private const val LIMITED_STARTER_WEAPON = "wooden_stick"
    }
}
