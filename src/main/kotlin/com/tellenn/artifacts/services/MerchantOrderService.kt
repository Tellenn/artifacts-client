package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

sealed class MerchantMissionResult {
    object Accepted : MerchantMissionResult()
    data class Rejected(val reason: String) : MerchantMissionResult()
}

/**
 * Achats chez un marchand fixe (hors événement) déclenchés par un humain (dashboard).
 * Pré-checks read-only (le marchand vend bien l'item, devise suffisante en banque) puis
 * mission trader en priorité HUMAN_ORDER — une seule mission humaine à la fois (sinon rejet).
 */
@Service
class MerchantOrderService(
    private val merchantService: MerchantService,
    private val threadService: ThreadService,
    private val accountClient: AccountClient,
) {

    companion object {
        private val logger = LogManager.getLogger(MerchantOrderService::class.java)
        private const val TRADER = "Aerith"
    }

    fun requestBuy(itemCode: String, quantity: Int): MerchantMissionResult {
        val merchant = merchantService.findFixedMerchantSelling(itemCode)
            ?: return MerchantMissionResult.Rejected("Aucun marchand fixe ne vend $itemCode")
        val price = merchant.buyPrice!!

        val affordable = merchantService.affordableQuantity(merchant.currency, price)
        if (affordable < quantity) {
            return MerchantMissionResult.Rejected(
                "${merchant.currency} insuffisant en banque : $quantity $itemCode demandés, de quoi en acheter $affordable"
            )
        }

        val assigned = threadService.assignMissionAsync(TRADER, MissionPriority.HUMAN_ORDER) {
            val character = accountClient.getCharacter(TRADER).data
            merchantService.buyFromFixedMerchant(character, itemCode, quantity)
        }
        return if (assigned) {
            logger.info("Mission achat {} x{} chez {} acceptée pour {}", itemCode, quantity, merchant.npc, TRADER)
            MerchantMissionResult.Accepted
        } else {
            MerchantMissionResult.Rejected("$TRADER est déjà sur une mission prioritaire")
        }
    }
}
