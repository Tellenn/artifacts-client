package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.NpcItem
import com.tellenn.artifacts.models.SimpleItem
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

@Service
class MerchantService (
    private val npcClient: NpcClient,
    private val bankService: BankService,
    private val movementService: MovementService,
    private val itemService: ItemService,
    private val accountClient: AccountClient,
){

    companion object {
        private val logger = LogManager.getLogger(MerchantService::class.java)
        private const val MIN_SELL_PRICE = 99

        // Items d'événement à acheter jusqu'à une quantité cible possédée (équipés + inventaires
        // + banque, tous personnages). La limite « une fois par saison » est auto-portée par le
        // comptage : cible atteinte = plus d'achat. La liste grandira — ajouter une entrée suffit.
        private val ONE_TIME_PURCHASE_TARGETS = mapOf(
            "lost_world_map" to 5,
        )

        private val EQUIPMENT_SLOTS = listOf(
            "weapon_slot", "rune_slot", "shield_slot", "helmet_slot", "body_armor_slot",
            "leg_armor_slot", "boots_slot", "ring1_slot", "ring2_slot", "amulet_slot",
            "artifact1_slot", "artifact2_slot", "artifact3_slot", "bag_slot",
        )
    }

    /**
     * Returns the items the given NPC will buy that are actually worth selling and that we own
     * in the bank. All checks here are read-only, so callers can decide whether a selling mission
     * is worthwhile before interrupting a character's thread.
     */
    fun findSellableItems(npcName: String): List<NpcItem> {
        val items = npcClient.getNpcItems(npcName).data
            .filter { it.sellPrice != null
                    && it.sellPrice > MIN_SELL_PRICE
                    && itemService.getItem(it.code).craft == null
                    && npcClient.getItemsBoughtWith(it.code).total == 0
                    && bankService.isInBank(it.code, 1) }
        logger.info("Found ${items.size} sellable npcItems for the event $npcName")
        return items
    }

    fun sellBankItemTo(chararacter : ArtifactsCharacter, npcName : String) : ArtifactsCharacter{
        var newCharacter = chararacter

        val items = findSellableItems(npcName)
        if(items.isEmpty()){
            logger.info("No npc items found for the event $npcName")
            return newCharacter
        }

        newCharacter = movementService.moveToBank(newCharacter)
        newCharacter = bankService.emptyInventory(newCharacter)
        for(item in items){
            if(bankService.isInBank(item.code, 1)){
                logger.info("Selling item ${item.code} to $npcName")
                newCharacter = movementService.moveToBank(newCharacter)
                newCharacter = bankService.withdrawAllOfOne(newCharacter, item.code)

                logger.info("Withdrawn the items, moving to the npc")
                newCharacter = movementService.moveToNpc(newCharacter, npcName)
                val itemToSell = newCharacter.inventory.first { it.code == item.code }

                logger.info("Now we sell ${itemToSell.quantity} ${itemToSell.code} to $npcName")
                newCharacter = npcClient.sellItem(newCharacter.name, item.code, itemToSell.quantity).data.character

            }
        }
        return newCharacter
    }

    fun buy(itemCode: String, character: ArtifactsCharacter): ArtifactsCharacter {
        val merchant = npcClient.getNpcByItemCode(itemCode).data.firstOrNull()
        val goldBank = bankService.getBankDetails().gold
        var newCharacter = character
        if(merchant != null && merchant.buyPrice != null && goldBank > merchant.buyPrice){
            newCharacter = movementService.moveToBank(character)
            newCharacter = bankService.withdrawGold( merchant.buyPrice, newCharacter)
            newCharacter = movementService.moveToNpc(newCharacter, merchant.npc)
            newCharacter = npcClient.buyItem(newCharacter.name, itemCode, 1).data.character
        }
        return newCharacter
    }

    /**
     * Items du registre d'achats uniques que ce NPC vend et qu'il reste à acquérir :
     * manquant > 0 et or en banque suffisant pour au moins un exemplaire.
     * Read-only — permet de décider d'interrompre un personnage sans lancer de mission.
     */
    fun findPendingOneTimePurchases(npcCode: String): List<NpcItem> {
        val candidates = npcClient.getNpcItems(npcCode).data
            .filter { it.buyPrice != null && it.currency == "gold" && it.code in ONE_TIME_PURCHASE_TARGETS }
        if (candidates.isEmpty()) return emptyList()

        val characters = accountClient.getCharacters().data
        val bankGold = bankService.getBankDetails().gold
        return candidates.filter { missingQuantity(it.code, characters) > 0 && bankGold >= it.buyPrice!! }
    }

    /**
     * Achète les items du registre encore manquants vendus par ce NPC, plafonné par l'or
     * en banque, puis dépose les achats en banque (équipables ensuite par les autres personnages).
     * Achat partiel possible : le prochain spawn du NPC complètera.
     */
    fun buyOneTimePurchases(character: ArtifactsCharacter, npcCode: String): ArtifactsCharacter {
        var newCharacter = character
        for (npcItem in findPendingOneTimePurchases(npcCode)) {
            val price = npcItem.buyPrice?.takeIf { it > 0 } ?: continue
            val missing = missingQuantity(npcItem.code, accountClient.getCharacters().data)
            val affordable = bankService.getBankDetails().gold / price
            val toBuy = minOf(missing, affordable)
            if (toBuy <= 0) continue

            logger.info("Buying {} {} from {} for {} gold", toBuy, npcItem.code, npcCode, toBuy * price)
            newCharacter = movementService.moveToBank(newCharacter)
            // Si buyItem échoue après le retrait, l'or reste dans l'inventaire du personnage
            // (pas de re-dépôt) : rien n'est perdu et le comptage au prochain spawn se recale.
            newCharacter = bankService.withdrawGold(toBuy * price, newCharacter)
            newCharacter = movementService.moveToNpc(newCharacter, npcCode)
            newCharacter = npcClient.buyItem(newCharacter.name, npcItem.code, toBuy).data.character
            newCharacter = movementService.moveToBank(newCharacter)
            newCharacter = bankService.deposit(newCharacter, listOf(SimpleItem(npcItem.code, toBuy)))
        }
        return newCharacter
    }

    private fun missingQuantity(itemCode: String, characters: List<ArtifactsCharacter>): Int {
        val target = ONE_TIME_PURCHASE_TARGETS[itemCode] ?: return 0
        return (target - countOwned(itemCode, characters)).coerceAtLeast(0)
    }

    private fun countOwned(itemCode: String, characters: List<ArtifactsCharacter>): Int {
        val equipped = characters.sumOf { character -> EQUIPMENT_SLOTS.count { character[it] == itemCode } }
        val inInventories = characters.sumOf { character ->
            character.inventory.filter { it.code == itemCode }.sumOf { it.quantity }
        }
        return equipped + inInventories + bankService.quantityInBank(itemCode)
    }
}