package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.EventClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.MerchantOffer
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
    private val eventClient: EventClient,
){

    companion object {
        private val logger = LogManager.getLogger(MerchantService::class.java)
        private const val MIN_SELL_PRICE = 99

        // Devise « or » : solde monétaire de la banque, pas un item stockable.
        private const val GOLD = "gold"

        // Échange perfect_pearl (artifact) contre des small_pearls chez un marchand fixe (hors
        // événement). On achète jusqu'à en posséder PERFECT_PEARL_TARGET (équipés + inventaires +
        // banque, tous personnages). Tant que la cible n'est pas atteinte, les small_pearls sont
        // gardées pour l'achat ; une fois atteinte, elles deviennent vendables.
        private const val PERFECT_PEARL = "perfect_pearl"
        private const val SMALL_PEARLS = "small_pearls"
        private const val PERFECT_PEARL_TARGET = 5

        // Items d'événement à acheter jusqu'à une quantité cible possédée (équipés + inventaires
        // + banque, tous personnages). La limite « une fois par saison » est auto-portée par le
        // comptage : cible atteinte = plus d'achat. La liste grandira — ajouter une entrée suffit.
        private val ONE_TIME_PURCHASE_TARGETS = mapOf(
            "lost_world_map" to 5,
            "voidstone_pickaxe" to 1,
            "voidstone_fishing_rod" to 1,
            "voidstone_gloves" to 1,
            "voidstone_axe" to 1
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
        val npcItems = npcClient.getNpcItems(npcName).data
        val genericlySellable = npcItems
            .filter { it.sellPrice != null
                    && it.sellPrice > MIN_SELL_PRICE
                    && itemService.getItem(it.code).craft == null
                    && npcClient.getItemsBoughtWith(it.code).total == 0
                    && bankService.isInBank(it.code, 1) }

        // Les small_pearls sont une monnaie (exclue du filtre générique) : on ne les vend qu'une
        // fois la réserve de perfect_pearl atteinte, sinon on les garde pour l'échange.
        val sellablePearls = npcItems
            .filter { it.code == SMALL_PEARLS && it.sellPrice != null && shouldSellSmallPearls() }

        val items = genericlySellable + sellablePearls
        logger.info("Found ${items.size} sellable npcItems for the event $npcName")
        return items
    }

    private fun shouldSellSmallPearls(): Boolean =
        countOwned(PERFECT_PEARL, accountClient.getCharacters().data) >= PERFECT_PEARL_TARGET
                && bankService.isInBank(SMALL_PEARLS, 1)

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

    /**
     * Marchand fixe (hors événement) vendant [itemCode], avec sa devise et son prix unitaire.
     * Un marchand est « d'événement » si son code figure dans /events?type=npc ; on les exclut.
     * Null si aucun marchand fixe ne le vend ou si le prix est absent/invalide. Read-only.
     */
    fun findFixedMerchantSelling(itemCode: String): NpcItem? {
        val eventNpcs = eventNpcCodes()
        return npcClient.getNpcByItemCode(itemCode).data
            .firstOrNull { (it.buyPrice ?: 0) > 0 && it.npc !in eventNpcs }
    }

    private fun eventNpcCodes(): Set<String> =
        eventClient.getEvents(type = "npc").data.map { it.content.code }.toSet()

    /**
     * Catalogue des items achetables chez un marchand fixe (hors événement) : devise, prix
     * unitaire, solde de la devise en banque et nombre d'exemplaires finançables. Le solde par
     * devise est calculé une seule fois (l'or est partagé par beaucoup d'offres). Read-only.
     */
    fun listBuyableItems(): List<MerchantOffer> {
        val eventNpcs = eventNpcCodes()
        val offers = npcClient.getAllNpcItems()
            .filter { (it.buyPrice ?: 0) > 0 && it.npc !in eventNpcs }
        val balanceByCurrency = offers.map { it.currency }.distinct()
            .associateWith { currencyInBank(it) }
        return offers
            .map { offer ->
                val price = offer.buyPrice!!
                val inBank = balanceByCurrency.getValue(offer.currency)
                MerchantOffer(offer.code, offer.npc, offer.currency, price, inBank, inBank / price)
            }
            .sortedBy { it.code }
    }

    private fun currencyInBank(currency: String): Int =
        if (currency == GOLD) bankService.getBankDetails().gold else bankService.quantityInBank(currency)

    /**
     * Achète [quantity] [itemCode] chez le marchand fixe qui le vend, plafonné par la devise
     * disponible en banque (or ou item selon la devise du marchand). Déclenché par le dashboard
     * (mission humaine). Achat partiel possible si la banque ne couvre pas toute la quantité.
     */
    fun buyFromFixedMerchant(character: ArtifactsCharacter, itemCode: String, quantity: Int): ArtifactsCharacter {
        val merchant = findFixedMerchantSelling(itemCode) ?: return character
        val price = merchant.buyPrice!!
        val toBuy = minOf(quantity, affordableQuantity(merchant.currency, price))
        if (toBuy <= 0) return character
        val cost = toBuy * price

        logger.info("Buying {} {} from {} for {} {}", toBuy, itemCode, merchant.npc, cost, merchant.currency)
        var newCharacter = movementService.moveToBank(character)
        newCharacter = withdrawCurrency(merchant.currency, cost, newCharacter)
        newCharacter = movementService.moveToNpc(newCharacter, merchant.npc)
        newCharacter = npcClient.buyItem(newCharacter.name, itemCode, toBuy).data.character
        newCharacter = movementService.moveToBank(newCharacter)
        return bankService.deposit(newCharacter, listOf(SimpleItem(itemCode, toBuy)))
    }

    /** Quantité maximale achetable avec la devise disponible en banque au prix unitaire [price]. */
    fun affordableQuantity(currency: String, price: Int): Int =
        if (currency == GOLD) bankService.getBankDetails().gold / price
        else bankService.quantityInBank(currency) / price

    private fun withdrawCurrency(currency: String, amount: Int, character: ArtifactsCharacter): ArtifactsCharacter =
        if (currency == GOLD) bankService.withdrawGold(amount, character)
        else bankService.withdrawOne(currency, amount, character)

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