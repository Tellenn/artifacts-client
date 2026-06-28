package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.NpcItem
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

    companion object {
        private const val MIN_SELL_PRICE = 99
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
}