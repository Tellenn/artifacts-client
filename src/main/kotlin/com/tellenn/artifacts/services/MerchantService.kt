package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.SimpleItem
import org.springframework.stereotype.Service

@Service
class MerchantService (
    private val npcClient: NpcClient,
    private val bankService: BankService,
    private val movementService: MovementService
){

    fun sellBankItemTo(chararacter : ArtifactsCharacter, npcName : String) : ArtifactsCharacter{
        var newCharacter = chararacter
        val items = npcClient.getNpcItems(npcName).data
                .filter { it.sellPrice != null }

        if(items.isNotEmpty()){
            // TODO Search in bank and fetch
            for(item in items){
                if(bankService.isInBank(item.code, 1)){
                    newCharacter = bankService.withdrawAllOfOne(newCharacter, item.code)
                }
            }
            newCharacter = movementService.moveToNpc(newCharacter, npcName)
            for(item in items){
                val itemToSell = newCharacter.inventory?.first { it.code == item.code }
                newCharacter = npcClient.sellItem(npcName, item.code, itemToSell?.quantity ?: 1)
            }

        }
        return newCharacter
    }
}