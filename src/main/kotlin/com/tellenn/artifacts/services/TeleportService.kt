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
     * Potion la plus appropriée en inventaire pour rejoindre [destinationMapId] :
     * match exact sur le mapId en priorité, sinon, parmi les potions de la même
     * région qui nous rapprochent, celle qui atterrit au plus près de la cible.
     */
    fun findPotionForDestination(character: ArtifactsCharacter, destinationMapId: Int): ItemDetails? {
        val usable = findUsableTeleportPotionsInInventory(character)
        if (usable.isEmpty()) return null

        usable.find { (_, mapId) -> mapId == destinationMapId }?.first?.let { return it }

        val destination = mapMongoClient.getMapById(destinationMapId) ?: return null
        val destinationRegion = destination.region ?: return null
        return usable
            .filter { (_, mapId) -> mapMongoClient.getMapById(mapId)?.region == destinationRegion }
            .filter { (_, landingMapId) -> bringsCloser(character, landingMapId, destination) }
            .minByOrNull { (_, landingMapId) -> landingDistance(landingMapId, destination) }
            ?.first
    }

    /**
     * Garde-fou : vrai si atterrir sur [landingMapId] rapproche réellement de
     * [destination]. Si le personnage est déjà dans la région cible, la potion
     * n'est retenue que si le point d'arrivée est plus proche que sa position
     * actuelle ; en cas de changement de région, rejoindre la bonne région est
     * toujours un gain.
     */
    private fun bringsCloser(character: ArtifactsCharacter, landingMapId: Int, destination: MapData): Boolean {
        val characterRegion = mapMongoClient.getMapById(character.mapId)?.region
        if (characterRegion != destination.region) return true

        val currentDistance = abs(character.x - destination.x) + abs(character.y - destination.y)
        return landingDistance(landingMapId, destination) < currentDistance
    }

    /** Distance (en cases) entre le point d'arrivée [landingMapId] et [destination]. */
    private fun landingDistance(landingMapId: Int, destination: MapData): Int {
        val landing = mapMongoClient.getMapById(landingMapId) ?: return Int.MAX_VALUE
        return abs(landing.x - destination.x) + abs(landing.y - destination.y)
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
    }
}
