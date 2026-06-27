package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CraftingClient
import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.exceptions.GENoOrdersException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.RecipeIngredient
import com.tellenn.artifacts.exceptions.CharacterInventoryFullException
import com.tellenn.artifacts.exceptions.MapContentNotFoundException
import com.tellenn.artifacts.exceptions.CharacterSkillTooLow
import com.tellenn.artifacts.exceptions.MissingItemException
import com.tellenn.artifacts.models.SimpleItem
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

/**
 * Service for managing gathering operations.
 * Provides functionality for gathering resources with inventory management.
 */
@Service
class GatheringService(
    private val gatheringClient: GatheringClient,
    private val mapService: MapService,
    private val movementService: MovementService,
    private val bankService: BankService,
    private val characterService: CharacterService,
    private val craftingClient: CraftingClient,
    private val resourceService: ResourceService,
    private val itemService: ItemService,
    private val battleService: BattleService,
    private val equipmentService: EquipmentService,
    private val accountClient: AccountClient,
    private val npcClient: NpcClient,
    private val grandExchangeService: GrandExchangeService
) {
    private val log = LogManager.getLogger(GatheringService::class.java)

    companion object {
        private const val INVENTORY_SAFE_MARGIN = 5
        private const val ENHANCED_RECYCLE_MIN_LEVEL = 20
        private const val ENHANCED_RECYCLE_MIN_BANK_GOLD = 20000
    }

    /*
     * The goal of this function is to be able to craft any item with any complexity as long as it's possible within a single inventory
     * It can gather, craft, fight, buy in order to be able to craft
     * The goal is to have everything available in the bank, and then fetch everything from the bank
     */
    fun craftOrGather(character: ArtifactsCharacter, itemCode: String, quantity: Int, functionLevel: Int = 0, allowFight: Boolean = false, shouldTrain: Boolean = true) : ArtifactsCharacter{

        val itemDetails = itemService.getItem(itemCode)
        val sizeForOne = itemService.getInvSizeToCraft(itemDetails)
        val inventorySizeNeeded = quantity * sizeForOne
        require( quantity >0 && inventorySizeNeeded <= character.inventoryMaxItems){"Cannot craft or gather $quantity item with code $itemCode because the inventory is too small"}

        if(functionLevel > 0 && bankService.isInBank(itemDetails.code, quantity)) {
            // It's a sub item and I have it in stock — reserve it so other agents can't claim it
            bankService.reserveInBank(itemDetails.code, quantity)
            return character
        }
        return when {
            itemDetails.subtype == "task" -> {
                // It's a task reward item, and we don't have it in stock
                tradeTaskItem(character, itemDetails, quantity)
            }

            itemDetails.subtype == "mob" -> {
                // It's a monster loot
                fightToGet(character, itemDetails, quantity, allowFight, shouldTrain)
            }

            itemDetails.subtype == "npc" -> {
                // We don't have it, and it's a npc selling it
                tradeNpc(character, itemDetails, quantity, functionLevel, allowFight, shouldTrain)
            }

            itemDetails.code == "wooden_stick" -> {
                // Specific for tutorial item
                characterService.unequip(character, "weapon", 1)
            }

            itemDetails.craft == null -> {
                // If there is no craft, check GC before gathering (only for ingredients)
                if (functionLevel > 0 && grandExchangeService.shouldBuyFromGC(character, itemDetails, quantity))
                    buyFromGC(character, itemDetails, quantity)
                else
                    gather(character, itemDetails, quantity)
            }

            else -> {
                // Check GC before crafting (only for ingredients)
                if (functionLevel > 0 && grandExchangeService.shouldBuyFromGC(character, itemDetails, quantity))
                    return buyFromGC(character, itemDetails, quantity)

                // Otherwise we craft (and call the same function for it)
                var newCharacter = character
                val itemsToWithdraw = mutableListOf<SimpleItem>()
                for (i in itemDetails.craft.items) {
                    newCharacter =
                        craftOrGather(newCharacter, i.code, i.quantity * quantity, functionLevel + 1, allowFight, shouldTrain)
                    // This is to empty the inventory if we need to stock up on something with a side product.
                    // It avoid the inventoryFullException when fighting mostly
                    newCharacter = movementService.moveToBank(newCharacter)
                    newCharacter = bankService.emptyInventory(newCharacter)
                    itemsToWithdraw.add(SimpleItem(i.code, i.quantity * quantity))
                }
                newCharacter = movementService.moveToBank(newCharacter)
                for (item in itemsToWithdraw) {
                    newCharacter = try {
                        bankService.withdrawOne(item.code, item.quantity, newCharacter)
                    } catch (_: MissingItemException) {
                        // L'item a été retiré par un autre agent entre la réservation et le retrait effectif
                        log.warn("{} : ingrédient {} manquant en banque — re-collecte", newCharacter.name, item.code)
                        bankService.releaseReservation(item.code, item.quantity)
                        val reGathered = craftOrGather(newCharacter, item.code, item.quantity, 0, allowFight, shouldTrain)
                        val atBank = movementService.moveToBank(reGathered)
                        val emptied = bankService.emptyInventory(atBank)
                        bankService.withdrawOne(item.code, item.quantity, emptied)
                    }
                }
                // gather() calls emptyInventory() which may have deposited previously obtained ingredients to bank.
                // Re-withdraw any missing ingredients before crafting.
                newCharacter = recollectMissingIngredients(newCharacter, itemDetails.craft.items, quantity)
                craft(newCharacter, itemDetails, quantity)
            }
        }
    }

    private fun buyFromGC(character: ArtifactsCharacter, item: ItemDetails, quantity: Int): ArtifactsCharacter {
        return try {
            grandExchangeService.buyFromGC(character, item, quantity)
        } catch (e: GENoOrdersException) {
            log.warn("Achat GC échoué pour {} — repli sur gather/craft : {}", item.code, e.message)
            if (item.craft == null) gather(character, item, quantity)
            else throw e
        }
    }

    fun recycle(character: ArtifactsCharacter, item: ItemDetails, i: Int): ArtifactsCharacter {
        var newCharacter = character
        val enhancedCost = enhancedRecycleCostOrNull(item, i)
        if (enhancedCost != null) {
            log.info("{} recycles {} (x{}) in enhanced mode for {} gold", newCharacter.name, item.code, i, enhancedCost)
            newCharacter = movementService.moveToBank(newCharacter)
            newCharacter = bankService.withdrawMoney(newCharacter, enhancedCost)
        }
        val mapData = mapService.findClosestMap(character = newCharacter, contentCode = item.craft?.skill)
        newCharacter = movementService.moveCharacterToCell(mapData.mapId, newCharacter)
        return craftingClient.recycle(newCharacter.name, item.code, i, enhanced = enhancedCost != null).data.character
    }

    /**
     * Renvoie le coût en or d'un recyclage « enhanced » lorsque les conditions sont réunies
     * (équipement de niveau 20+, au moins 20 000 or en banque et de quoi couvrir le coût),
     * sinon `null` pour un recyclage normal.
     *
     * Coût = somme des quantités d'ingrédients de la recette × quantité recyclée × tarif par ingrédient.
     */
    private fun enhancedRecycleCostOrNull(item: ItemDetails, quantity: Int): Int? {
        val craft = item.craft ?: return null
        if (item.level < ENHANCED_RECYCLE_MIN_LEVEL) return null
        val bankGold = bankService.getBankDetails().gold
        if (bankGold < ENHANCED_RECYCLE_MIN_BANK_GOLD) return null
        val totalIngredients = craft.items.sumOf { it.quantity }
        val cost = totalIngredients * quantity * goldPerIngredient(item.level)
        return if (bankGold >= cost) cost else null
    }

    private fun goldPerIngredient(itemLevel: Int): Int = when {
        itemLevel <= 20 -> 5
        itemLevel <= 30 -> 10
        itemLevel <= 40 -> 15
        itemLevel <= 45 -> 20
        else -> 25
    }

    private fun gather(character: ArtifactsCharacter, item: ItemDetails, quantityToCraft: Int) : ArtifactsCharacter{
        val levelToGather = item.level
        val skillLevel = when (item.subtype) {
            "mining" -> character.miningLevel
            "woodcutting" -> character.woodcuttingLevel
            "fishing" -> character.fishingLevel
            "alchemy" -> character.alchemyLevel
            else -> throw IllegalArgumentException("Invalid item subtype: ${item.subtype}")
        }
        if(levelToGather > skillLevel){
            throw CharacterSkillTooLow("Insufficient level to gather ${item.code}", item.subtype, skillLevel)
        }

        var quantityGathered = 0
        val resourceCode = resourceService.findResourceContaining(item.code, skillLevel).code
        val excludedMapIds = mutableSetOf<Int>()
        var mapData = mapService.findClosestMap(character = character, contentCode = resourceCode)
        var newCharacter = equipmentService.equipBestToolForSkill(character, item.subtype)
        newCharacter = equipmentService.equipBestAvailableEquipmentForCraftingOrGatheringInBank(newCharacter)
        newCharacter = movementService.moveToBank(newCharacter)
        newCharacter = bankService.emptyInventory(newCharacter)
        newCharacter = movementService.moveCharacterToCell(mapData.mapId, newCharacter)
        while (quantityGathered < quantityToCraft) {
            val inventoryUsed = newCharacter.inventory.sumOf { it.quantity }
            if (inventoryUsed >= newCharacter.inventoryMaxItems - INVENTORY_SAFE_MARGIN) {
                log.debug("{} inventaire presque plein ({}/{}) — vidage préventif avant le prochain gather", newCharacter.name, inventoryUsed, newCharacter.inventoryMaxItems)
                newCharacter = movementService.moveToBank(newCharacter)
                newCharacter = bankService.emptyInventory(newCharacter)
                newCharacter = movementService.moveCharacterToCell(mapData.mapId, newCharacter)
            }
            try{
                val gatherResult = gatheringClient.gather(characterName = newCharacter.name).data
                quantityGathered += gatherResult.details.items
                    .filter { it.code == item.code }
                    .sumOf { it.quantity }
                newCharacter = gatherResult.character
            }catch (e: CharacterInventoryFullException){
                log.warn("{} inventaire plein malgré la vérification préventive — vidage", newCharacter.name, e)
                newCharacter = accountClient.getCharacter(newCharacter.name).data
                newCharacter = movementService.moveToBank(newCharacter)
                newCharacter = bankService.emptyInventory(newCharacter)
                newCharacter = movementService.moveCharacterToCell(mapData.mapId, newCharacter)
            }catch (_: MapContentNotFoundException){
                log.warn("{} : ressource {} introuvable sur la map {} — données de map potentiellement obsolètes", newCharacter.name, resourceCode, mapData.mapId)
                excludedMapIds.add(mapData.mapId)
                newCharacter = accountClient.getCharacter(newCharacter.name).data
                mapData = mapService.findClosestMap(character = newCharacter, contentCode = resourceCode, excludeMapIds = excludedMapIds)
                newCharacter = movementService.moveCharacterToCell(mapData.mapId, newCharacter)
            }
        }
        return newCharacter
    }

    private fun recollectMissingIngredients(character: ArtifactsCharacter, ingredients: List<RecipeIngredient>, quantity: Int): ArtifactsCharacter {
        val missingIngredients = ingredients.filter { ingredient ->
            val inInventory = character.inventory.filter { it.code == ingredient.code }.sumOf { it.quantity }
            inInventory < ingredient.quantity * quantity
        }
        if (missingIngredients.isEmpty()) return character
        log.debug("Re-collecting {} ingredient(s) from bank deposited during gathering", missingIngredients.size)
        var newCharacter = movementService.moveToBank(character)
        missingIngredients.forEach { ingredient ->
            val inInventory = newCharacter.inventory.filter { it.code == ingredient.code }.sumOf { it.quantity }
            val needed = ingredient.quantity * quantity - inInventory
            if (needed > 0) {
                newCharacter = bankService.withdrawOne(ingredient.code, needed, newCharacter)
            }
        }
        return newCharacter
    }

    private fun craft(character: ArtifactsCharacter, item: ItemDetails, quantity: Int) : ArtifactsCharacter {
        val skill = item.craft?.skill
        if(skill != null && item.level > character.getLevelOf(skill)){
            throw CharacterSkillTooLow(skill = skill, level = item.level)
        }
        var newCharacter = equipmentService.equipBestAvailableEquipmentForCraftingOrGatheringInBank(character)
        val mapData = mapService.findClosestMap(character = newCharacter, contentCode = skill)
        newCharacter = movementService.moveCharacterToCell(mapData.mapId, newCharacter)
        newCharacter = craftingClient.craft(newCharacter.name, item.code, quantity).data.character

        return newCharacter
    }

    private fun tradeNpc(character: ArtifactsCharacter, item: ItemDetails, quantity: Int, functionLevel: Int = 0, allowFight: Boolean = false, shouldTrain: Boolean = false): ArtifactsCharacter {
        // We don't have it, and it's a npc selling it
        val npcItem = npcClient.getNpcByItemCode(item.code).data.first()
        require(!(npcItem.currency == "gold" || npcItem.buyPrice == null)) { "Will not buy component with gold currency" }
        val currencyCode = npcItem.currency
        val currencyQty = npcItem.buyPrice * quantity
        var newCharacter = craftOrGather(character, currencyCode, currencyQty, functionLevel + 1, allowFight, shouldTrain)
        newCharacter = movementService.moveToBank(newCharacter)
        newCharacter = bankService.emptyInventory(newCharacter)
        newCharacter = try {
            bankService.withdrawOne(currencyCode, currencyQty, newCharacter)
        } catch (_: MissingItemException) {
            // La devise a été retirée par un autre agent entre la réservation et le retrait effectif
            log.warn("{} : devise {} volée par un autre agent — re-collecte", newCharacter.name, currencyCode)
            bankService.releaseReservation(currencyCode, currencyQty)
            val reGathered = craftOrGather(newCharacter, currencyCode, currencyQty, 0, allowFight, shouldTrain)
            val atBank = movementService.moveToBank(reGathered)
            val emptied = bankService.emptyInventory(atBank)
            bankService.withdrawOne(currencyCode, currencyQty, emptied)
        }
        newCharacter = movementService.moveToNpc(newCharacter, npcItem.npc)
        return npcClient.buyItem(newCharacter.name, npcItem.code, quantity).data.character
    }

    private fun tradeTaskItem(character: ArtifactsCharacter, itemDetails: ItemDetails, quantity: Int): ArtifactsCharacter{
        // We don't have it and it's a task item
        val npcItem = npcClient.getNpcItems("tasks_trader")
            .data
            .filter { it.buyPrice != null }
            .first { itemDetails.code == it.code }
        if(npcItem.buyPrice != null && bankService.isInBank("tasks_coin", npcItem.buyPrice.times(quantity).plus(10))){
            var newCharacter = movementService.moveToBank(character)
            newCharacter = bankService.withdrawOne("tasks_coin", npcItem.buyPrice.times(quantity), newCharacter)
            newCharacter = movementService.moveToNpc(newCharacter, npcItem.npc)
            return npcClient.buyItem(newCharacter.name, npcItem.code, quantity).data.character
        }else{
            throw MissingItemException() // TODO : Better exception
        }
    }

    private fun fightToGet(character: ArtifactsCharacter, itemDetails: ItemDetails, quantity: Int, allowFight: Boolean = false, shouldTrain: Boolean = false): ArtifactsCharacter{
        // We don't have it and it's a mob item
        if(allowFight){
            val newCharacter = movementService.moveToBank(character)
            return bankService.storeItemsToDoThenGetThemBack(newCharacter, movementService) {
                battleService.fightToGetItem(accountClient.getCharacter(newCharacter.name).data, itemDetails.code, quantity, shouldTrain)
            }
        }else{
            throw IllegalArgumentException("Cannot gather mob without fighting enabled")
        }
    }
}
