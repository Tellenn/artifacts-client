package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.BankRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.apache.logging.log4j.LogManager
import org.springframework.data.domain.Pageable
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

        val inventory = character.inventory
        inventory?.forEach { item ->
            val itemsFound = itemRepository.findByCode(item.code, Pageable.unpaged())
            if (!itemsFound.isEmpty) {
                if(bankRepository.findByCode(item.code, Pageable.unpaged()).isEmpty){
                    bankRepository.insert<BankItemDocument>(BankItemDocument.fromItemDetails(ItemDocument.toItemDetails(itemsFound.first()), item.quantity))
                }else{
                    val existingBankItem = bankRepository.findByCode(item.code, Pageable.unpaged()).first()
                    val updatedQuantity = existingBankItem.quantity + item.quantity
                    val updatedBankItem = existingBankItem.copy(quantity = updatedQuantity)
                    bankRepository.save(updatedBankItem)
                }
            }
        }

        return character
    }
}
