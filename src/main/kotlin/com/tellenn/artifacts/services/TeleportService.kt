package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.db.clients.MapMongoClient
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

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
     * Meilleure potion pour rejoindre [destinationMapId] :
     * match exact sur le mapId, sinon une potion dont la destination est
     * dans la même région que la cible. Le match exact est préféré.
     */
    fun findPotionForDestination(character: ArtifactsCharacter, destinationMapId: Int): ItemDetails? {
        val usable = findUsableTeleportPotionsInInventory(character)
        if (usable.isEmpty()) return null

        usable.find { (_, mapId) -> mapId == destinationMapId }?.first?.let { return it }

        val destinationRegion = mapMongoClient.getMapById(destinationMapId)?.region ?: return null
        return usable.find { (_, mapId) ->
            mapMongoClient.getMapById(mapId)?.region == destinationRegion
        }?.first
    }

    /**
     * Potion en inventaire dont la destination est une map contenant une banque.
     */
    fun findBankPotionInInventory(character: ArtifactsCharacter): ItemDetails? {
        val bankMapIds = bankMapIds()
        return findUsableTeleportPotionsInInventory(character)
            .find { (_, mapId) -> mapId in bankMapIds }
            ?.first
    }

    /**
     * Potion disponible en banque (pas en inventaire) dont la destination est
     * une banque et dont les conditions d'utilisation sont remplies.
     */
    fun findBankPotionAvailableInBank(character: ArtifactsCharacter): ItemDetails? {
        val bankMapIds = bankMapIds()
        return bankItemRepository.findByEffectsCode(TELEPORT_EFFECT)
            .filter { it.quantity > 0 }
            .mapNotNull { bankDoc ->
                val item = itemRepository.findByCode(bankDoc.code)
                val destMapId = item.effects?.find { it.code == TELEPORT_EFFECT }?.value
                if (destMapId != null && destMapId in bankMapIds && canUse(character, item)) item
                else null
            }
            .firstOrNull()
    }

    /**
     * Utilise une potion depuis l'inventaire et retourne le personnage mis à jour
     * (déjà téléporté à destination).
     */
    fun use(character: ArtifactsCharacter, itemCode: String): ArtifactsCharacter {
        log.info("{} utilise la potion de téléport : {}", character.name, itemCode)
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

    private fun bankMapIds(): Set<Int> =
        mapMongoClient.getMaps(content_code = "bank").data.map { it.mapId }.toSet()

    companion object {
        private const val TELEPORT_EFFECT = "teleport"
    }
}
