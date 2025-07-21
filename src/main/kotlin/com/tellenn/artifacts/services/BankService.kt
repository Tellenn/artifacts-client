package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.ItemDetails
import com.tellenn.artifacts.clients.models.SimpleItem
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

@Service
class BankService(
    private val bankClient: BankClient,
    private val bankRepository: BankItemRepository,
    private val itemRepository: ItemRepository,
    private val mapProximityService: MapProximityService,
    private val movementService: MovementService
) {
    private val log = LogManager.getLogger(BankService::class.java)

    /**
     * Moves a character to the closest bank if they're not already there.
     *
     * @param character The character to move to the bank
     * @return The updated character after moving to the bank, or the original character if already at a bank
     */
    fun moveToBank(character: ArtifactsCharacter): ArtifactsCharacter {
        log.debug("Checking if character ${character.name} needs to move to a bank")

        // Find the closest bank
        val closestBank = mapProximityService.findClosestMap(character = character, contentCode = "bank")
        log.debug("Closest bank for character ${character.name} is at position (${closestBank.x}, ${closestBank.y})")

        // Check if the character is already at the bank
        if (character.x == closestBank.x && character.y == closestBank.y) {
            log.debug("Character ${character.name} is already at the bank at position (${closestBank.x}, ${closestBank.y})")
            return character
        }

        // Move the character to the bank
        log.debug("Moving character ${character.name} to bank at position (${closestBank.x}, ${closestBank.y})")
        return movementService.moveCharacterToCell(closestBank.x, closestBank.y, character)
    }

    fun emptyInventory(character: ArtifactsCharacter) : ArtifactsCharacter{
        val inventory = character.inventory ?: return character
        moveToBank(character)
        // Store original bank state for potential rollback
        val originalBankItems = mutableMapOf<String, BankItemDocument>()
        val newBankItems = mutableListOf<BankItemDocument>()

        try {
            // Process inventory items and update database
            val itemsToDeposit = mutableListOf<SimpleItem>()

            inventory.forEach { item ->
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
                bankClient.depositItems(character.name, itemsToDeposit)
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

        return character
    }

    fun fetchItems(item: String?, quantityLeft: Int, newCharacter: ArtifactsCharacter): ArtifactsCharacter {
        // Implementation for fetching items from the bank
        return newCharacter
    }

    fun isInBank(item: String?, quantityLeft: Int = 1): Boolean {
        val bankedItem = bankClient.getBankedItems(item).data.firstOrNull()
        if(bankedItem == null){
            return false
        }
        return bankedItem.quantity >= quantityLeft
    }

    fun getAllEquipmentsUnderLevel(level: Int) : List<ItemDetails>{
        val dbItems = bankRepository.findByTypeAndLevelIsLessThanEqual("equipment", level)
        return dbItems.map { itemDocument -> BankItemDocument.toItemDetails(itemDocument) }
    }
}
