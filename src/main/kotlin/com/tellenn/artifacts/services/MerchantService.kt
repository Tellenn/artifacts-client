package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.SimpleItem
import lombok.extern.slf4j.Slf4j
import okhttp3.internal.concurrent.TaskRunner.Companion.logger
import org.springframework.stereotype.Service

@Slf4j
@Service
class MerchantService (
    private val npcClient: NpcClient,
    private val bankService: BankService,
    private val movementService: MovementService,
    private val itemService: ItemService
){

    fun sellBankItemTo(chararacter : ArtifactsCharacter, npcName : String) : ArtifactsCharacter{
        var newCharacter = chararacter

        logger.info("!!!!!!!! Getting the npcItems for the event $npcName")
        val items = npcClient.getNpcItems(npcName).data
                .filter { it.sellPrice != null
                        && it.sellPrice > 99
                        && itemService.getItem(it.code).craft == null
                        && npcClient.getItemsBoughtWith(it.code).total == 0}
        logger.info("!!!!!!!! Found ${items.size} npcItems for the event $npcName")
        if(items.isNotEmpty()){
            newCharacter = bankService.emptyInventory(chararacter)
            for(item in items){
                logger.info("!!!!!!!! Selling item ${item.code} to $npcName")
                if(bankService.isInBank(item.code, 1)){

                    logger.info("!!!!!!!! We found ${item.code} in bank")
                    newCharacter = bankService.moveToBank(newCharacter)
                    newCharacter = bankService.withdrawAllOfOne(newCharacter, item.code)

                    logger.info("!!!!!!!! Withdrawn the items, moving to the npc")
                    newCharacter = movementService.moveToNpc(newCharacter, npcName)
                    val itemToSell = newCharacter.inventory.first { it.code == item.code }

                    logger.info("!!!!!!!! Now we sell ${itemToSell.quantity} ${itemToSell.code} to $npcName")
                    newCharacter = npcClient.sellItem(npcName, item.code, itemToSell.quantity).data.character

                }
            }
        }else{
            logger.info("!!!!!!!! No npcItems found for the event $npcName")
        }
        return newCharacter
    }
}