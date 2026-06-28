package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.db.clients.MapMongoClient
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.models.SimpleItem
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import kotlin.math.abs

/**
 * Centralise toute la logique liée aux potions de téléportation :
 * recherche en inventaire et en banque, vérification des conditions
 * d'utilisation, et appel effectif de [CharacterClient.useItem].
 *
 * Une potion de téléport est un item dont un effet a le code "teleport".
 * La `value` de cet effet est le mapId de destination.
 *
 * Ce service ne dépend ni de MovementService ni de BankService afin
 * d'éviter toute dépendance circulaire.
 */
@Service
class TeleportService(
    private val characterClient: CharacterClient,
    private val itemRepository: ItemRepository,
    private val bankItemRepository: BankItemRepository,
    private val accountClient: AccountClient,
    private val mapMongoClient: MapMongoClient,
    private val mapService: MapService,
) {
    private val log = LogManager.getLogger(TeleportService::class.java)

    /**
     * Toutes les potions de téléport utilisables présentes en inventaire,
     * associées à leur mapId de destination.
     */
    fun findUsableTeleportPotionsInInventory(character: ArtifactsCharacter): List<Pair<ItemDetails, Int>> {
        val codes = character.inventory
            .filter { it.quantity > 0 && it.code.isNotBlank() }
            .map { it.code }
        if (codes.isEmpty()) return emptyList()

        return itemRepository.findByCodeIn(codes)
            .filter { item -> item.effects?.any { it.code == TELEPORT_EFFECT } == true && canUse(character, item) }
            .mapNotNull { item ->
                val destMapId = item.effects?.find { it.code == TELEPORT_EFFECT }?.value
                destMapId?.let { item to it }
            }
    }

    /**
     * Première potion d'inventaire qui économise plus de [MIN_CELLS_SAVED] cases
     * de marche pour rejoindre [destinationMapId], en comparant le trajet à pied
     * depuis la position actuelle au trajet depuis le point d'arrivée de la potion.
     * Dès qu'une potion convient, on la retourne sans examiner les suivantes.
     */
    fun findPotionForDestination(character: ArtifactsCharacter, destinationMapId: Int): ItemDetails? {
        val usable = findUsableTeleportPotionsInInventory(character)
        if (usable.isEmpty()) return null

        val destination = mapMongoClient.getMapById(destinationMapId) ?: return null
        val currentRegion = mapMongoClient.getMapById(character.mapId)?.region
        val currentCost = walkingCost(character.x, character.y, currentRegion, destination)

        return usable.firstNotNullOfOrNull { (item, landingMapId) ->
            val landing = mapMongoClient.getMapById(landingMapId) ?: return@firstNotNullOfOrNull null
            val costWithPotion = walkingCost(landing.x, landing.y, landing.region, destination)
            if (currentCost - costWithPotion > MIN_CELLS_SAVED) item else null
        }
    }

    /**
     * Coût de marche (en cases) d'un point vers [destination] : distance de
     * Manhattan, majorée de [PENALTY_PER_TRANSITION] cases par transition de
     * région à franchir lorsque l'origine et la destination changent de région.
     */
    private fun walkingCost(fromX: Int, fromY: Int, fromRegion: Int?, destination: MapData): Int {
        val manhattan = abs(fromX - destination.x) + abs(fromY - destination.y)
        val transitions =
            if (fromRegion != null && destination.region != null && fromRegion != destination.region) {
                mapService.findTransitionPath(fromRegion, destination.region!!).size
            } else {
                0
            }
        return manhattan + transitions * PENALTY_PER_TRANSITION
    }

    /**
     * Toutes les potions de téléport disponibles en banque (une entrée par code),
     * dont les conditions d'utilisation sont remplies, avec la quantité à retirer.
     * On retire une seule potion par type pour pouvoir choisir la plus adaptée à
     * chaque déplacement, sauf pour la potion de rappel dont on prend deux unités
     * lorsque le stock le permet (déplacement de secours fréquemment utilisé).
     */
    fun findUsableTeleportPotionsInBank(character: ArtifactsCharacter): List<SimpleItem> =
        bankItemRepository.findByEffectsCode(TELEPORT_EFFECT)
            .filter { it.quantity > 0 }
            .groupBy { it.code }
            .mapNotNull { (code, docs) ->
                val item = itemRepository.findByCode(code)
                if (item.effects?.any { it.code == TELEPORT_EFFECT } == true && canUse(character, item)) {
                    SimpleItem(code, withdrawQuantity(code, docs.sumOf { it.quantity }))
                } else {
                    null
                }
            }

    /**
     * Quantité de potion à retirer de la banque : deux pour la potion de rappel
     * (dans la limite du stock), une seule pour tout autre type.
     */
    private fun withdrawQuantity(code: String, available: Int): Int =
        if (code == RECALL_POTION) minOf(RECALL_WITHDRAW_TARGET, available) else 1

    /**
     * Utilise une potion depuis l'inventaire et retourne le personnage mis à jour
     * (déjà téléporté à destination).
     */
    fun use(character: ArtifactsCharacter, itemCode: String): ArtifactsCharacter {
        return characterClient.useItem(character.name, itemCode, 1).data.character
    }

    /**
     * Vérifie que toutes les conditions d'utilisation d'un item sont satisfaites.
     * Un item sans conditions est toujours utilisable. L'opérateur "cost" et tout
     * opérateur inconnu échouent volontairement (fail-safe — jamais automatique).
     */
    fun canUse(character: ArtifactsCharacter, item: ItemDetails): Boolean =
        item.conditions?.all { condition ->
            when (condition.operator) {
                "achievement_unlocked" -> accountClient
                    .getAccountAchievements(character.account, true)
                    .data.any { it.code == condition.code }
                "gt"       -> character.getStat(condition.code) > condition.value
                "lt"       -> character.getStat(condition.code) < condition.value
                "eq"       -> character.getStat(condition.code) == condition.value
                "ne"       -> character.getStat(condition.code) != condition.value
                "has_item" -> character.inventory.any {
                    it.code == condition.code && it.quantity >= condition.value
                }
                else -> false
            }
        } ?: true

    companion object {
        private const val TELEPORT_EFFECT = "teleport"
        private const val RECALL_POTION = "recall_potion"
        private const val RECALL_WITHDRAW_TARGET = 2

        /** Économie minimale (en cases) pour qu'une potion vaille la peine d'être utilisée. */
        private const val MIN_CELLS_SAVED = 3

        /** Coût (en cases) imputé à chaque transition de région franchie à pied. */
        private const val PENALTY_PER_TRANSITION = 5
    }
}
