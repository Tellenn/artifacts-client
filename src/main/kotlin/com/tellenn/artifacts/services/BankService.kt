package com.tellenn.artifacts.services

import com.tellenn.artifacts.AppConfig
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.exceptions.BankCorruptedException
import com.tellenn.artifacts.exceptions.MapContentNotFoundException
import com.tellenn.artifacts.exceptions.MissingItemException
import com.tellenn.artifacts.exceptions.NotFoundException
import com.tellenn.artifacts.models.BankDetails
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service
class BankService(
    private val bankClient: BankClient,
    private val bankRepository: BankItemRepository,
    private val itemRepository: ItemRepository,
    private val characterService: CharacterService,
    private val bankItemSyncService: BankItemSyncService,
    private val accountClient: AccountClient,
    private val movementClient: MovementClient,
    private val mapService: MapService,
    private val teleportService: TeleportService
) {
    private val log = LogManager.getLogger(BankService::class.java)
    private val reservations = ConcurrentHashMap<String, AtomicInteger>()

    fun reserveInBank(code: String, quantity: Int) {
        reservations.getOrPut(code) { AtomicInteger(0) }.addAndGet(quantity)
        log.debug("Réservé {} x{} en banque", code, quantity)
    }

    fun releaseReservation(code: String, quantity: Int) {
        reservations.computeIfPresent(code) { _, atomic ->
            val remaining = atomic.addAndGet(-quantity)
            if (remaining <= 0) null else atomic
        }
        log.debug("Libéré réservation {} x{}", code, quantity)
    }

    fun emptyInventory(character: ArtifactsCharacter) : ArtifactsCharacter{
        var newCharacter = character

        // Potions de téléport déjà en inventaire : on en garde une de chaque type
        // (inutile de les déposer pour les reprendre juste après).
        val heldPotionCodes = teleportService.findUsableTeleportPotionsInInventory(character)
            .map { it.first.code }
            .toSet()

        val inventory = character.inventory
        val items = inventory
            .filter { it.quantity > 0 }
            .map { slot ->
                val kept = if (slot.code in heldPotionCodes) 1 else 0
                SimpleItem(slot.code, slot.quantity - kept)
            }
            .filter { it.quantity > 0 }
        newCharacter = deposit(newCharacter, items)
        if(newCharacter.utility1Slot != ""){
            val utility1Code = newCharacter.utility1Slot
            val utility1Qty = newCharacter.utility1SlotQuantity
            newCharacter = characterService.unequip(newCharacter, "utility1", utility1Qty)
            newCharacter = deposit(newCharacter, listOf(SimpleItem(utility1Code, utility1Qty)))
        }
        if(newCharacter.utility2Slot != ""){
            val utility2Code = newCharacter.utility2Slot
            val utility2Qty = newCharacter.utility2SlotQuantity
            newCharacter = characterService.unequip(newCharacter, "utility2", utility2Qty)
            newCharacter = deposit(newCharacter, listOf(SimpleItem(utility2Code, utility2Qty)))
        }
        newCharacter = depositMoney(newCharacter, newCharacter.gold)

        // On complète avec une potion de chaque type encore disponible en banque,
        // pour pouvoir choisir la plus adaptée à chaque déplacement.
        val potionsToWithdraw = teleportService.findUsableTeleportPotionsInBank(newCharacter)
            .filter { it.code !in heldPotionCodes }
        if (potionsToWithdraw.isNotEmpty()) {
            log.info("{} restocks teleport potions: {}", newCharacter.name,
                potionsToWithdraw.joinToString { it.code })
            newCharacter = withdrawMany(ArrayList(potionsToWithdraw), newCharacter)
        }
        return newCharacter
    }

    fun depositMoney(character: ArtifactsCharacter, amount: Int) : ArtifactsCharacter{
        if(amount <= 0){
            return character
        }
        return bankClient.depositGold(character.name, amount).data.character
    }

    fun withdrawMoney(character: ArtifactsCharacter, amount: Int) : ArtifactsCharacter{
        if(amount <= 0){
            return character
        }
        return bankClient.withdrawGold(character.name, amount).data.character
    }

    fun deposit(character: ArtifactsCharacter, items: List<SimpleItem> ): ArtifactsCharacter {
        var newCharacter = character

        val filteredItems = items.filter { it.quantity > 0 || it.code.isNotEmpty() }

        // Store original bank state for potential rollback
        val originalBankItems = mutableMapOf<String, BankItemDocument>()
        val newBankItems = mutableListOf<BankItemDocument>()

        try {
            // Process inventory items and update database
            val itemsToDeposit = mutableListOf<SimpleItem>()

            filteredItems.forEach { item ->
                if(item.code.isNotEmpty()){
                    val itemsFound = itemRepository.findByCode(item.code)

                    val existingBankItem = bankRepository.findByCode(item.code)
                    if(existingBankItem == null){
                        val newBankItem = BankItemDocument.fromItemDetails(itemsFound, item.quantity)
                        bankRepository.insert(newBankItem)
                        newBankItems.add(newBankItem)
                    }else{
                        originalBankItems[item.code] = existingBankItem
                        val updatedQuantity = existingBankItem.quantity + item.quantity
                        val updatedBankItem = existingBankItem.copy(quantity = updatedQuantity)
                        bankRepository.save(updatedBankItem)
                    }

                    // Add item to the list for API call
                    itemsToDeposit.add(SimpleItem(item.code, item.quantity))
                }
            }

            // Make the API call to deposit items
            if (itemsToDeposit.isNotEmpty()) {
                try {
                    newCharacter = bankClient.depositItems(character.name, itemsToDeposit).data.character
                }catch (_: MapContentNotFoundException){

                    newCharacter = accountClient.getCharacter(newCharacter.name).data
                    val closestBank = mapService.findClosestMap(newCharacter, "bank")
                    newCharacter = movementClient.move(newCharacter.name, closestBank.mapId).data.character
                    return deposit(newCharacter, items)
                }
            }

        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to deposit items to bank: ${e.message}")

            newBankItems.forEach {
                try { bankRepository.delete(it) }
                catch (ex: Exception) { log.error("Failed to rollback new bank item ${it.code}: ${ex.message}") }
            }
            originalBankItems.forEach { (_, originalItem) ->
                try { bankRepository.save(originalItem) }
                catch (ex: Exception) { log.error("Failed to rollback bank item ${originalItem.code}: ${ex.message}") }
            }

            throw e
        }

        return newCharacter
    }

    fun withdrawOne(itemCode: String, quantity: Int, character: ArtifactsCharacter): ArtifactsCharacter {
        // Implementation for fetching items from the bank
        return withdrawMany(ArrayList(listOf(SimpleItem(itemCode, quantity))), character)
    }

    fun isInBank(item: String?, quantityLeft: Int = 1): Boolean {
        val bankedItem = bankClient.getBankedItems(item).data.firstOrNull() ?: return false
        val reserved = reservations[item]?.get() ?: 0
        return bankedItem.quantity - reserved >= quantityLeft
    }

    fun getAllEquipmentsUnderLevel(level: Int) : List<BankItemDocument>{
        val dbItem = ArrayList<BankItemDocument>()
        dbItem.addAll(bankRepository.findByTypeInAndLevelIsLessThanEqual(
            listOf("helmet", "ring", "weapon", "amulet", "artifact", "boots", "leg_armor", "body_armor", "rune", "bag", "shield", "artifact"), level))
        return dbItem
    }

    fun getAllResources() : List<BankItemDocument>{
        val dbItem = ArrayList<BankItemDocument>()
        dbItem.addAll(bankRepository.findByTypeInAndLevelIsLessThanEqual(
            listOf("resource"), AppConfig.maxLevel))
        return dbItem
    }

    fun withdrawMany(items: ArrayList<SimpleItem>, character: ArtifactsCharacter): ArtifactsCharacter {
        if(items.isEmpty()){
            return character
        }
        var newCharacter = character
        try {
            newCharacter = bankClient.withdrawItems(newCharacter.name, items).data.character
            items.forEach { releaseReservation(it.code, it.quantity) }
        }catch (e: BankCorruptedException){
            bankItemSyncService.syncAllItems()
            throw e
        }catch (e: MissingItemException){
            // Peut arriver si un autre agent a retiré l'item entre le isInBank et le retrait effectif
            bankItemSyncService.syncAllItems()
            throw e
        }catch (e: NotFoundException){
            // 404 = l'item n'existe plus du tout dans la vraie banque : le cache local est périmé
            bankItemSyncService.syncAllItems()
            throw e
        }

        // Remove items from the local database
        try {
            items.forEach { item ->
                val bankItem = bankRepository.findByCode(item.code)
                if (bankItem != null) {
                    val newQuantity = bankItem.quantity - item.quantity
                    if (newQuantity <= 0) {
                        // If quantity reaches 0 or less, remove the entry completely
                        bankRepository.delete(bankItem)
                        log.debug("Removed item ${item.code} from bank database as quantity reached 0")
                    } else {
                        // Otherwise update with the new quantity
                        val updatedBankItem = bankItem.copy(quantity = newQuantity)
                        bankRepository.save(updatedBankItem)
                        log.debug("Updated item ${item.code} quantity to $newQuantity in bank database")
                    }
                } else {
                    log.warn("Attempted to withdraw item ${item.code} that was not found in local database")
                }
            }
        } catch (e: Exception) {
            log.error("Failed to update local database after withdrawing items: ${e.message}")
            // We don't rollback the API call as it's already been made
        }
        
        return newCharacter
    }

    fun getBankDetails() : BankDetails {
        return bankClient.getBankDetails().data
    }

    fun getBankSize() : Int{
        return bankRepository.count().toInt()
    }

    fun buyBankSlot(character: ArtifactsCharacter): ArtifactsCharacter {
        return bankClient.buyBankExpansion(character.name).data.character
    }

    fun canCraftFromBank(item: ItemDetails?, quantity: Int = 1): Boolean {
        if(item == null){return false}
        var canCraft = true
        if(item.craft == null){
            return isInBank(item.code,quantity)
        }else{
            for(i in item.craft.items){
                canCraft = canCraft && (
                            isInBank(i.code,quantity * i.quantity) ||
                            canCraftFromBank(itemRepository.findByCode(i.code), quantity * i.quantity)
                        )

            }
        }
        return canCraft
    }

    /**
     * CAREFUL
     * THIS CAUSE A DESYNC WHEN USING THE CALLBACK. THE CALLBACK SHOULD REFRESH THE CHARACTER DATA
     */
    fun storeItemsToDoThenGetThemBack(character: ArtifactsCharacter, movementService: MovementService, callable : () -> ArtifactsCharacter) : ArtifactsCharacter {
        val oldInventory = character.inventory.filter { it.code != "" }.map { SimpleItem(it.code, it.quantity)}
        val oldMapId = character.mapId
        var newCharacter = movementService.moveToBank(character)
        newCharacter = emptyInventory(newCharacter)
        movementService.moveCharacterToCell(oldMapId, newCharacter)
        callable()
        // TODO : When gathering, sometime you get extra items and fail to fetch the previous items, how to prevent this ?
        newCharacter = accountClient.getCharacter(newCharacter.name).data // To make sure depending on the callable
        newCharacter = movementService.moveToBank(newCharacter)
        newCharacter = withdrawMany(ArrayList(oldInventory), newCharacter)
        return newCharacter


    }

    fun withdrawAllOfOne(newCharacter: ArtifactsCharacter, code: String): ArtifactsCharacter {
        val bankedItem = bankClient.getBankedItems(code).data.firstOrNull()
        if(bankedItem == null){
            return newCharacter
        }
        val quantityLeft = bankedItem.quantity
        return withdrawOne(code, quantityLeft, newCharacter)
    }

    fun getAll(): List<SimpleItem> {
        return bankRepository.findAll().map { SimpleItem(it.code, it.quantity) }
    }

    fun getOne(itemCode: String): SimpleItem {
        return bankRepository.findByCode(itemCode)?.let { SimpleItem(it.code, it.quantity) } ?: SimpleItem("", 0)
    }

    fun getHealingPotions() : List<ItemDetails> {
        return bankRepository.findByCodeContainingIgnoreCase("health").map { itemRepository.findByCode(it.code) }
    }

    /**
     * Returns every combat potion (subtype "potion") currently stored in the bank.
     * Effect-based categorization (heal / damage / resistance) is left to [EquipmentService].
     */
    fun getCombatPotions() : List<ItemDetails> {
        val bankCodes = getAll().map { it.code }
        return itemRepository.findByCodeIn(bankCodes).filter { it.subtype == "potion" }
    }

    fun withdrawGold(amount: Int, newCharacter: ArtifactsCharacter) : ArtifactsCharacter{
        return bankClient.withdrawGold(newCharacter.name, amount).data.character
    }
}
