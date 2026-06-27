package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.GrandExchangeClient
import com.tellenn.artifacts.exceptions.GENoOrdersException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.GEOrder
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.MapData
import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

@Service
class GrandExchangeService(
    private val grandExchangeClient: GrandExchangeClient,
    private val itemService: ItemService,
    private val mapService: MapService,
    private val monsterService: MonsterService,
    private val resourceService: ResourceService,
    private val movementService: MovementService,
    private val bankService: BankService,
    @param:Value("\${artifacts.economy.gold-per-second}") private val goldPerSecond: Double,
) {
    companion object {
        private val log = LogManager.getLogger(GrandExchangeService::class.java)
        private const val GATHER_COOLDOWN_SECONDS = 25
        private const val TRAVEL_SECONDS_PER_TILE = 5
        private const val MIN_GC_UNIT_PRICE = 5
        // Rough estimate: monster takes hp/10 seconds to kill (assumes ~10 DPS net)
        private const val ESTIMATED_DPS = 10
    }

    fun shouldBuyFromGC(character: ArtifactsCharacter, item: ItemDetails, quantity: Int): Boolean {
        val lowestOrder = getLowestGCOrder(item.code) ?: return false
        if (lowestOrder.quantity < quantity) return false

        val unitPrice = lowestOrder.price
        if (unitPrice <= MIN_GC_UNIT_PRICE) {
            log.info("GE: {} at {}g/u — below minimum threshold, buying directly", item.code, unitPrice)
            return true
        }

        val estimatedSeconds = estimateSecondsToObtain(item, quantity, character)
        if (estimatedSeconds == Int.MAX_VALUE) return false

        val maxGoldToPay = estimatedSeconds * goldPerSecond
        val totalGcCost = unitPrice.toLong() * quantity

        return if (totalGcCost <= maxGoldToPay) {
            log.info("GE worthwhile for {} x{}: {}g (GE) <= {}g estimated ({} s × {}/s)",
                item.code, quantity, totalGcCost, maxGoldToPay, estimatedSeconds, goldPerSecond)
            true
        } else {
            false
        }
    }

    fun buyFromGC(character: ArtifactsCharacter, item: ItemDetails, quantity: Int): ArtifactsCharacter {
        val lowestOrder = getLowestGCOrder(item.code)
            ?: throw GENoOrdersException("Aucune offre GC disponible pour ${item.code}")
        val goldNeeded = lowestOrder.price * quantity

        var newCharacter = movementService.moveToBank(character)
        newCharacter = bankService.withdrawGold(goldNeeded, newCharacter)
        newCharacter = movementService.moveToGrandExchange(newCharacter)
        newCharacter = grandExchangeClient.buyItem(newCharacter.name, item.code, quantity, lowestOrder.price).data.character

        log.info("{} bought {} x{} from GE for {}g/u", newCharacter.name, item.code, quantity, lowestOrder.price)
        return newCharacter
    }

    fun estimateSecondsToObtain(item: ItemDetails, quantity: Int, character: ArtifactsCharacter): Int =
        when {
            item.subtype == "task" || item.subtype == "npc" -> Int.MAX_VALUE
            item.subtype == "mob" -> estimateFightSeconds(item, quantity, character)
            item.craft == null -> estimateGatherSeconds(item, quantity, character)
            else -> estimateCraftSeconds(item, quantity, character)
        }

    private fun estimateGatherSeconds(item: ItemDetails, quantity: Int, character: ArtifactsCharacter): Int {
        val resource = runCatching { resourceService.findResourceContaining(item.code, Int.MAX_VALUE) }.getOrNull()
            ?: return Int.MAX_VALUE
        val resourceMap = runCatching { mapService.findClosestMap(character = character, contentCode = resource.code) }.getOrNull()
            ?: return Int.MAX_VALUE
        val travelSeconds = (euclideanDistance(character, resourceMap) * TRAVEL_SECONDS_PER_TILE).toInt()
        return travelSeconds + GATHER_COOLDOWN_SECONDS * quantity
    }

    private fun estimateFightSeconds(item: ItemDetails, quantity: Int, character: ArtifactsCharacter): Int {
        val monster = monsterService.findMonsterThatDrop(item.code) ?: return Int.MAX_VALUE
        val drop = monster.drops?.firstOrNull { it.code == item.code } ?: return Int.MAX_VALUE
        val monsterMap = runCatching { monsterService.findMonsterMap(monster.code) }.getOrNull()
            ?: return Int.MAX_VALUE

        val travelSeconds = (euclideanDistance(character, monsterMap) * TRAVEL_SECONDS_PER_TILE).toInt()
        val avgDropPerFight = (drop.minQuantity + drop.maxQuantity) / 2.0
        val fightsNeeded = ceil(quantity * drop.rate / avgDropPerFight).toInt()
        val avgFightSeconds = (monster.hp.toDouble() / ESTIMATED_DPS).toInt().coerceAtLeast(4)

        return travelSeconds + fightsNeeded * avgFightSeconds
    }

    private fun estimateCraftSeconds(item: ItemDetails, quantity: Int, character: ArtifactsCharacter): Int {
        val ingredientsTime = item.craft!!.items.fold(0) { acc, ingredient ->
            if (acc == Int.MAX_VALUE) return Int.MAX_VALUE
            val subItem = itemService.getItem(ingredient.code)
            val subTime = estimateSecondsToObtain(subItem, ingredient.quantity * quantity, character)
            if (subTime == Int.MAX_VALUE) Int.MAX_VALUE else acc + subTime
        }
        return if (ingredientsTime == Int.MAX_VALUE) Int.MAX_VALUE else ingredientsTime + 5 * quantity
    }

    private fun getLowestGCOrder(itemCode: String): GEOrder? =
        runCatching { grandExchangeClient.getPublicSellOrders(itemCode).data.minByOrNull { it.price } }.getOrNull()

    private fun euclideanDistance(character: ArtifactsCharacter, map: MapData): Double {
        val dx = (character.x - map.x).toDouble()
        val dy = (character.y - map.y).toDouble()
        return sqrt(dx.pow(2) + dy.pow(2))
    }
}
