package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.BankRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class BankService(
    private val bankClient: BankClient,
    private val bankRepository: BankItemRepository,
    private val itemRepository: ItemRepository
) {


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
