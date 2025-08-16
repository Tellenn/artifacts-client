package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.BankDetails
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

@Service
class BankService(
    private val bankClient: BankClient,
    private val bankRepository: BankItemRepository,
    private val itemRepository: ItemRepository,
    private val mapService: MapService,
    private val movementService: MovementService,
    private val characterService: CharacterService
) {
    private val log = LogManager.getLogger(BankService::class.java)

    /**
     * Moves a character to the closest bank if they're not already there.
     *
     * @param character The character to move to the bank
     * @return The updated character after moving to the bank, or the original character if already at a bank
     */
    fun moveToBank(character: ArtifactsCharacter): ArtifactsCharacter {
        val closestBank = mapService.findClosestMap(character = character, contentCode = "bank")
        if (character.x == closestBank.x && character.y == closestBank.y) {
            return character
        }
        return movementService.moveCharacterToCell(closestBank.x, closestBank.y, character)
    }

    fun emptyInventory(character: ArtifactsCharacter) : ArtifactsCharacter{
        val inventory = character.inventory
        val items = inventory.filter { it.quantity > 0 }.map { SimpleItem(it.code, it.quantity) }
        var newCharacter = deposit(character, items)
        if(newCharacter.utility1Slot != ""){
            newCharacter = characterService.unequip(newCharacter, "utility1", newCharacter.utility1SlotQuantity)
            newCharacter = deposit(character, listOf(SimpleItem(newCharacter.utility1Slot, newCharacter.utility1SlotQuantity)))
        }
        if(newCharacter.utility2Slot != ""){
            newCharacter = characterService.unequip(newCharacter, "utility2", newCharacter.utility2SlotQuantity)
            newCharacter = deposit(character, listOf(SimpleItem(newCharacter.utility2Slot, newCharacter.utility2SlotQuantity)))
        }
        return depositMoney(newCharacter, newCharacter.gold)
    }

    fun depositMoney(character: ArtifactsCharacter, amount: Int) : ArtifactsCharacter{
        if(amount <= 0){
            return character
        }
        var newCharacter = moveToBank(character)
        return bankClient.depositGold(newCharacter.name, amount).data.character
    }

    fun withdrawMoney(character: ArtifactsCharacter, amount: Int) : ArtifactsCharacter{
        if(amount <= 0){
            return character
        }
        var newCharacter = moveToBank(character)
        return bankClient.withdrawGold(newCharacter.name, amount).data.character
    }

    fun deposit(character: ArtifactsCharacter, items: List<SimpleItem> ): ArtifactsCharacter {
        var newCharacter = character

        val filteredItems = items.filter { it.quantity > 0 || it.code.isNotEmpty() }

        newCharacter = moveToBank(newCharacter)
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
                        val newBankItem = BankItemDocument.fromItemDetails(ItemDocument.toItemDetails(itemsFound), item.quantity)
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
                newCharacter = bankClient.depositItems(character.name, itemsToDeposit).data.character
            }

        } catch (e: Exception) {
            log.error("Failed to deposit items to bank: ${e.message}")

            // Rollback database changes
            newBankItems.forEach { 
                try {
                    bankRepository.delete(it)
                } catch (ex: Exception) {
                    log.error("Failed to rollback new bank item ${it.code}: ${ex.message}")
                }
            }

            originalBankItems.forEach { (_, originalItem) ->
                try {
                    bankRepository.save(originalItem)
                } catch (ex: Exception) {
                    log.error("Failed to rollback bank item ${originalItem.code}: ${ex.message}")
                }
            }

            // Re-throw the exception or handle it as needed
            // throw e
        }

        return newCharacter
    }

    fun withdrawOne(itemCode: String, quantity: Int, character: ArtifactsCharacter): ArtifactsCharacter {
        // Implementation for fetching items from the bank
        return withdrawMany(ArrayList(listOf(SimpleItem(itemCode, quantity))), character)
    }

    fun isInBank(item: String?, quantityLeft: Int = 1): Boolean {
        val bankedItem = bankClient.getBankedItems(item).data.firstOrNull()
        if(bankedItem == null){
            return false
        }
        return bankedItem.quantity >= quantityLeft
    }

    fun getAllEquipmentsUnderLevel(level: Int) : List<BankItemDocument>{
        val dbItem = ArrayList<BankItemDocument>()
        dbItem.addAll(bankRepository.findByTypeInAndLevelIsLessThanEqual(
            listOf("helmet", "ring", "weapon", "amulet", "artifact", "boots", "leg_armor", "body_armor", "rune", "bag", "shield"), level))
        return dbItem
    }

    fun withdrawMany(items: ArrayList<SimpleItem>, character: ArtifactsCharacter): ArtifactsCharacter {
        if(items.isEmpty()){
            return character
        }
        val newCharacter = bankClient.withdrawItems(character.name, items).data.character
        
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

    fun canCraftFromBank(item: ItemDetails, quantity: Int = 1): Boolean {
        var canCraft = true
        if(item.craft == null){
            return isInBank(item.code,quantity)
        }else{
            for(i in item.craft.items){
                canCraft = canCraft && (
                            isInBank(item.code,quantity) ||
                            canCraftFromBank(ItemDocument.toItemDetails(itemRepository.findByCode(i.code)), quantity * i.quantity)
                        )

            }
        }
        return canCraft
    }

    fun storeItemsToDoThenGetThemBack(character: ArtifactsCharacter, callable : () -> ArtifactsCharacter) : ArtifactsCharacter {
        val oldInventory = character.inventory?.filter { it.code != "" }?.map { SimpleItem(it.code, it.quantity)} ?: ArrayList()
        val oldx = character.x
        val oldy = character.y
        var newCharacter = emptyInventory(character)
        newCharacter = movementService.moveCharacterToCell(oldx, oldy, newCharacter)
        newCharacter = callable()
        // TODO : When gathering, sometime you get extra items and fail to fetch the previous items, how to prevent this ?
        newCharacter = moveToBank(newCharacter)
        newCharacter = withdrawMany(ArrayList(oldInventory), newCharacter)
        return newCharacter


    }

    fun withdrawAllOfOne(newCharacter: ArtifactsCharacter, code: String): ArtifactsCharacter {
        val bankedItem = bankClient.getBankedItems(code).data.firstOrNull()
        if(bankedItem == null){
            return newCharacter
        }
        moveToBank(newCharacter)
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
        return bankRepository.findByCodeContainingIgnoreCase("health").map { ItemDocument.toItemDetails(itemRepository.findByCode(it.code)) }
    }
}
