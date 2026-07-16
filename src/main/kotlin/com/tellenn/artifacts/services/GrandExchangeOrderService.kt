package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.GrandExchangeClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.GEOrder
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import kotlin.math.ceil

sealed class GeMissionResult {
    object Accepted : GeMissionResult()
    data class Rejected(val reason: String) : GeMissionResult()
}

/**
 * Missions Grand Exchange déclenchées par un humain (dashboard) : achat, création
 * d'ordre de vente, annulation. Pré-checks read-only puis mission Aerith en
 * priorité HUMAN_ORDER — une seule mission humaine à la fois (sinon rejet).
 */
@Service
class GrandExchangeOrderService(
    private val grandExchangeClient: GrandExchangeClient,
    private val bankService: BankService,
    private val movementService: MovementService,
    private val threadService: ThreadService,
    private val accountClient: AccountClient,
) {

    companion object {
        private val logger = LogManager.getLogger(GrandExchangeOrderService::class.java)
        private const val TRADER = "Aerith"

        // La création d'un ordre de vente prélève une taxe sur le prix total ; on
        // retire une marge d'or de la banque pour la couvrir (reliquat re-déposé).
        private const val SELL_TAX_BUFFER_RATE = 0.05
    }

    fun requestBuy(itemCode: String, quantity: Int, maxUnitPrice: Int): GeMissionResult {
        val selection = selectCheapestOrders(itemCode, quantity, maxUnitPrice)
            ?: return GeMissionResult.Rejected("Pas assez d'ordres de vente sous ${maxUnitPrice}g pour $itemCode x$quantity")

        val cost = selection.sumOf { (order, taken) -> order.price.toLong() * taken }
        val bankGold = bankService.getBankDetails().gold
        if (bankGold < cost) {
            return GeMissionResult.Rejected("Or en banque insuffisant : ${cost}g requis, ${bankGold}g disponibles")
        }

        return assignToTrader("buy $itemCode x$quantity (max ${maxUnitPrice}g/u)") {
            executeBuy(itemCode, quantity, maxUnitPrice)
        }
    }

    fun requestSell(itemCode: String, quantity: Int, unitPrice: Int): GeMissionResult {
        val inBank = bankService.quantityInBank(itemCode)
        if (inBank < quantity) {
            return GeMissionResult.Rejected("Stock banque insuffisant : $quantity $itemCode demandés, $inBank en banque")
        }

        return assignToTrader("sell $itemCode x$quantity à ${unitPrice}g/u") {
            executeSell(itemCode, quantity, unitPrice)
        }
    }

    fun requestCancel(orderId: String): GeMissionResult =
        assignToTrader("cancel order $orderId") {
            executeCancel(orderId)
        }

    private fun assignToTrader(description: String, mission: () -> Unit): GeMissionResult {
        val assigned = threadService.assignMissionAsync(TRADER, MissionPriority.HUMAN_ORDER, mission)
        return if (assigned) {
            logger.info("GE mission acceptée pour {}: {}", TRADER, description)
            GeMissionResult.Accepted
        } else {
            GeMissionResult.Rejected("$TRADER est déjà sur une mission prioritaire")
        }
    }

    private fun executeBuy(itemCode: String, quantity: Int, maxUnitPrice: Int) {
        // Les ordres sont re-sélectionnés au moment de l'exécution : le carnet a pu bouger.
        val selection = selectCheapestOrders(itemCode, quantity, maxUnitPrice)
        if (selection == null) {
            logger.warn("GE buy annulé : plus assez d'ordres sous {}g pour {} x{}", maxUnitPrice, itemCode, quantity)
            return
        }
        val cost = selection.sumOf { (order, taken) -> order.price.toLong() * taken }

        var character = accountClient.getCharacter(TRADER).data
        character = movementService.moveToBank(character)
        character = bankService.withdrawGold(cost.toInt(), character)
        character = movementService.moveToGrandExchange(character)
        for ((order, taken) in selection) {
            character = grandExchangeClient.buyItem(character.name, order.id, taken).data.character
            logger.info("{} a acheté {} x{} à {}g/u (ordre {})", TRADER, itemCode, taken, order.price, order.id)
        }
        depositEverything(character)
    }

    private fun executeSell(itemCode: String, quantity: Int, unitPrice: Int) {
        val taxBuffer = ceil(quantity.toLong() * unitPrice * SELL_TAX_BUFFER_RATE).toInt()

        var character = accountClient.getCharacter(TRADER).data
        character = movementService.moveToBank(character)
        character = bankService.withdrawOne(itemCode, quantity, character)
        if (taxBuffer > 0 && bankService.getBankDetails().gold >= taxBuffer) {
            character = bankService.withdrawGold(taxBuffer, character)
        }
        character = movementService.moveToGrandExchange(character)
        character = grandExchangeClient.createSellOrder(character.name, itemCode, quantity, unitPrice).data.character
        logger.info("{} a mis en vente {} x{} à {}g/u", TRADER, itemCode, quantity, unitPrice)
        depositEverything(character)
    }

    private fun executeCancel(orderId: String) {
        var character = accountClient.getCharacter(TRADER).data
        character = movementService.moveToGrandExchange(character)
        character = grandExchangeClient.cancelOrder(character.name, orderId).data.character
        logger.info("{} a annulé l'ordre {}", TRADER, orderId)
        depositEverything(character)
    }

    /** Retour banque : dépose les items ramenés et le reliquat d'or. */
    private fun depositEverything(character: ArtifactsCharacter) {
        var newCharacter = movementService.moveToBank(character)
        newCharacter = bankService.emptyInventory(newCharacter)
        if (newCharacter.gold > 0) {
            bankService.depositMoney(newCharacter, newCharacter.gold)
        }
    }

    /**
     * Ordres les moins chers couvrant [quantity] à prix unitaire <= [maxUnitPrice],
     * avec la quantité à prendre sur chacun. Null si le carnet ne suffit pas.
     */
    private fun selectCheapestOrders(
        itemCode: String,
        quantity: Int,
        maxUnitPrice: Int,
    ): List<Pair<GEOrder, Int>>? {
        val orders = runCatching { grandExchangeClient.getPublicSellOrders(itemCode).data }
            .getOrDefault(emptyList())
            .filter { it.price <= maxUnitPrice }
            .sortedBy { it.price }

        val selection = mutableListOf<Pair<GEOrder, Int>>()
        var remaining = quantity
        for (order in orders) {
            if (remaining <= 0) break
            val taken = minOf(remaining, order.quantity)
            selection.add(order to taken)
            remaining -= taken
        }
        return if (remaining > 0) null else selection
    }
}
