package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.SimpleItem
import org.springframework.stereotype.Service

@Service
class MerchantService (
    private val npcClient: NpcClient,
    private val bankService: BankService,
    private val movementService: MovementService,
    private val itemService: ItemService
){

    fun sellBankItemTo(chararacter : ArtifactsCharacter, npcName : String) : ArtifactsCharacter{
        var newCharacter = chararacter
        val items = npcClient.getNpcItems(npcName).data
                .filter { it.sellPrice != null
                        && it.sellPrice > 99
                        && itemService.getItem(it.code).craft == null
                        && npcClient.getItemsBoughtWith(it.code).total == 0}

        if(items.isNotEmpty()){
            for(item in items){
                if(bankService.isInBank(item.code, 1)){
                    newCharacter = bankService.withdrawAllOfOne(newCharacter, item.code)
                }
            }
            newCharacter = movementService.moveToNpc(newCharacter, npcName)
            for(item in items){
                val itemToSell = newCharacter.inventory.first { it.code == item.code }
                newCharacter = npcClient.sellItem(npcName, item.code, itemToSell.quantity ?: 1).data.character
            }

        }
        return newCharacter
    }
}