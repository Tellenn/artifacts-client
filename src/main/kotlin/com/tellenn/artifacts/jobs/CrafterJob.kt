package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.documents.CraftedItemDocument
import com.tellenn.artifacts.db.repositories.CraftedItemRepository
import com.tellenn.artifacts.exceptions.BattleLostException
import com.tellenn.artifacts.exceptions.CharacterInventoryFullException
import com.tellenn.artifacts.exceptions.CharacterSkillTooLow
import com.tellenn.artifacts.exceptions.MissingItemException
import com.tellenn.artifacts.exceptions.UnknownMapException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.EventService
import com.tellenn.artifacts.services.GatheringService
import com.tellenn.artifacts.services.ItemService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.AchievementService
import com.tellenn.artifacts.services.MonsterService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.TaskService
import com.tellenn.artifacts.services.UniqueArtifactService
import com.tellenn.artifacts.services.CharacterContextService
import com.tellenn.artifacts.AppConfig.maxLevel
import org.springframework.stereotype.Component
import java.lang.Thread.sleep
import kotlin.math.min

/**
 * Job implementation for characters with the "crafter" job.
 */
@Component
class CrafterJob(
    mapService: MapService,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    accountClient: AccountClient,
    taskService: TaskService,
    private val itemService: ItemService,
    private val craftedItemRepository: CraftedItemRepository,
    private val gatheringService: GatheringService,
    private val eventService: EventService,
    private val monsterService: MonsterService,
    private val achievementService: AchievementService,
    private val uniqueArtifactService: UniqueArtifactService,
    private val contextService: CharacterContextService,
) : GenericJob(mapService, movementService, bankService, characterService, accountClient, taskService) {

    lateinit var character: ArtifactsCharacter
    val rareItemCode = listOf("magical_cure", "jasper_crystal", "astralyte_crystal", "enchanted_fabric", "ruby", "sapphire", "emerald", "topaz", "diamond")
    var eventBasedItemCodes = listOf<String>()

    fun run(characterName: String) {
        sleep(1000)
        eventBasedItemCodes = eventService.getAllEventMaterials()
        character = init(characterName)
        do {
            if (character.weaponcraftingLevel >= maxLevel
                && character.gearcraftingLevel >= maxLevel
                && character.jewelrycraftingLevel >= maxLevel
            ) {
                contextService.setObjective(character.name, "Exécution des achievements (toutes compétences max)")
                character = achievementService.executeAchievement(character, "crafter")
                continue
            }

            val bankDetails = bankService.getBankDetails()
            if(bankDetails.slots - bankService.getBankSize() < 20 && bankDetails.slots < 200 && bankDetails.gold > bankDetails.nextExpansionCost){
                log.info("${character.name} is buying a bankSlot for ${bankDetails.nextExpansionCost}")
                contextService.setObjective(character.name, "Achat d'un slot bancaire (${bankDetails.nextExpansionCost} or)")
                character = movementService.moveToBank(character)
                character = bankService.withdrawMoney(character, bankDetails.nextExpansionCost)
                character = bankService.buyBankSlot(character)
            }

            contextService.setObjective(character.name, "Nettoyage de la banque")
            character = cleanUpBank()
            contextService.setObjective(character.name, "Réclamation des items en attente")
            character = claimPendingItems()

            for ((artifact, _) in uniqueArtifactService.findArtifactsToGather()) {
                contextService.setObjective(character.name, "Collecte de l'artéfact unique : ${artifact.code}")
                character = tryGatherUniqueArtifact(artifact)
            }

            val skillToLevel = getLowestSkillLevel(character)
            val itemsToCraft = getListOfItemToCraftUnderLevel(
                character,
                listOf("weaponcrafting","gearcrafting","jewelrycrafting")
            )
            //tryBossFight("king_slime","king_slimeball",1)

            if (!itemsToCraft.isEmpty()) {
                var instantCraft = false
                for (itemDetail in itemsToCraft) {
                    if(bankService.canCraftFromBank(itemDetail)){
                        try {
                            log.info("${character.name} is crafting a ${itemDetail.code} from items in bank for use in bank")
                            contextService.setObjective(character.name, "Craft de ${itemDetail.code} depuis la banque (stock)")
                            character =
                                gatheringService.craftOrGather(character, itemDetail.code, 1, allowFight = false)
                            character = movementService.moveToBank(character)
                            character = bankService.emptyInventory(character)
                            saveOrUpdateCraftedItem(itemDetail)
                            instantCraft = true
                            break
                        }catch (e: CharacterSkillTooLow){
                            log.warn("A sub component of the crafting of ${itemDetail.code} failed because the character has too low ${e.skill} level. Required : ${e.level}")
                        }
                    }
                }
                if(instantCraft){
                    continue
                }

                for (itemDetail in itemsToCraft) {
                    try{
                        log.info("${character.name} is gathering and crafting a ${itemDetail.code} for use in bank")
                        contextService.setObjective(character.name, "Collecte et craft de ${itemDetail.code} pour la banque")
                        character = gatheringService.craftOrGather(character, itemDetail.code, 1, allowFight = true, shouldTrain = false)
                        character = movementService.moveToBank(character)
                        character = bankService.emptyInventory(character)
                        saveOrUpdateCraftedItem(itemDetail)
                    }catch (e : UnknownMapException){
                        // Event monsters may not be on the map — this is expected, skip silently
                        log.info("Skipping ${itemDetail.code}: required monster not found on map (${e.message})")
                        character = accountClient.getCharacter(character.name).data
                    }catch (e: MissingItemException){
                        // This case happens when we try to craft something, but we fail to battle, but still try to craft the item
                        log.warn("Could not craft item ${itemDetail.code} because an item is missing, probably due to a lost fight. Should not happen again", e)
                        character = accountClient.getCharacter(character.name).data
                    }catch (e: BattleLostException){
                        log.warn("Failed to get items for crafting ${itemDetail.code}", e)
                        character = accountClient.getCharacter(character.name).data
                        character = movementService.moveToBank(character)
                        character = bankService.emptyInventory(character)
                    }catch (e: CharacterSkillTooLow){
                        log.warn("A sub component of the crafting of ${itemDetail.code} failed because the character has too low skill level")
                        log.debug(e)
                        character = accountClient.getCharacter(character.name).data
                        character = movementService.moveToBank(character)
                        character = bankService.emptyInventory(character)
                    } // TODO Another failure case can be because of an event base requirement. Need to do something about it
                }
            }
            val itemToCraft =
                getTopLowestCostingItemForLeveling(character.getLevelOf(skillToLevel), listOf(skillToLevel))
            // TODO : If itemTocraft is empty, it means the crafts are becoming too hard to do and may need to include rare items
            val oldLevel = character.getLevelOf(skillToLevel)
            contextService.setObjective(character.name, "Craft de ${itemToCraft.code} pour level up $skillToLevel (niv. $oldLevel)")
            while (oldLevel == character.getLevelOf(skillToLevel)) {
                try {
                    log.info("${character.name} is gathering and crafting a ${itemToCraft.code} for leveling")
                    character = gatheringService.craftOrGather(character, itemToCraft.code, 1, allowFight = true)
                    character = gatheringService.recycle(character, itemToCraft, 1)
                    character = movementService.moveToBank(character)
                    character = bankService.emptyInventory(character)
                }catch (e: CharacterSkillTooLow){
                    // Usually caused by a crating of a sub object, it can be nice if the main crafter level the sub resource
                    character = accountClient.getCharacter(character.name).data
                    do {
                        character = movementService.moveToBank(character)
                        character = bankService.emptyInventory(character)
                        val item =
                            itemService.getAllCraftableItemsBySkillAndMaxLevel(e.skill, character.getLevelOf(e.skill))
                                .first { it.code != "cursed_plank" && it.code != "magical_plank" && it.code != "strangold_bar" }
                        character = gatheringService.craftOrGather(
                            character,
                            item.code,
                            (character.inventoryMaxItems - 10) / itemService.getInvSizeToCraft(item)
                        )
                    }while (e.level == character.getLevelOf(e.skill))
                }catch(e: CharacterSkillTooLow){
                    log.warn("Tried to craft sub item from a too high level job", e)
                    continue
                }catch (_: CharacterInventoryFullException) {
                    log.warn("Character inventory is full, something went terribly wrong")
                    character = accountClient.getCharacter(character.name).data
                    character = movementService.moveToBank(character)
                    character = bankService.emptyInventory(character)
                    break
                }catch (e: Exception){
                    character = accountClient.getCharacter(character.name).data
                    log.error("Uncaught error occured while gathering in the event", e)
                    break
                }
            }
        }while (true)
    }

    /**
     * Attempts to craft one unit of the given unique artifact by gathering its boss-drop ingredients.
     * Boss fight simulation is handled transparently by [com.tellenn.artifacts.services.BattleService.fightToGetItem].
     * Returns the updated character after crafting and depositing to bank, or the refreshed character if the attempt fails.
     */
    private fun tryGatherUniqueArtifact(artifact: ItemDetails): ArtifactsCharacter {
        log.info("${character.name} is gathering unique artifact ${artifact.code}")
        return try {
            character = gatheringService.craftOrGather(character, artifact.code, 1, allowFight = true, shouldTrain = false)
            character = movementService.moveToBank(character)
            bankService.emptyInventory(character)
        } catch (_: BattleLostException) {
            log.warn("${character.name} cannot beat the boss required for ${artifact.code} yet")
            accountClient.getCharacter(character.name).data
        } catch (e: Exception) {
            log.warn("${character.name} failed to gather artifact ${artifact.code}: ${e.message}")
            accountClient.getCharacter(character.name).data
        }
    }

    private fun getLowestSkillLevel(character: ArtifactsCharacter): String{
        val skills = mapOf(
            "weaponcrafting"  to (character.weaponcraftingLevel  - character.weaponcraftingLevel  % 5),
            "gearcrafting"    to (character.gearcraftingLevel    - character.gearcraftingLevel    % 5),
            "jewelrycrafting" to (character.jewelrycraftingLevel - character.jewelrycraftingLevel % 5),
        )
        return skills.minWith(compareBy { it.value }).key
    }

    private fun getListOfItemToCraftUnderLevel(character : ArtifactsCharacter, skills : List<String>) : List<ItemDetails>{
        val items = ArrayList<ItemDetails>()


        for (skill in skills) {
            val minLevel = character.getLevelOf(skill) / 5 * 5
            items.addAll(itemService.getCrafterItemsBetweenLevel(minLevel-1, character.getLevelOf(skill) +1, listOf(skill)))
        }
        // Based on crafted history
        //val alreadyCraftedItem = craftedItemRepository.findAllByQuantityLessThan(3).map { it.code }

        // Or based on available bank items ?
        val availableCraftedItem = bankService.getAllEquipmentsUnderLevel(maxLevel).map { it.code }


        return items
            .filter { !availableCraftedItem.contains(it.code) }
            .filter {
                it.craft?.items?.none { item ->
                    if(rareItemCode.contains(item.code)){
                        !bankService.isInBank(item.code, item.quantity)
                        || ( it.level > 30 && bankService.isInBank(
                            "tasks_coins",
                            40
                        ))
                    }else{ false } } ?: false }
            .filter { it.craft?.items?.none { item ->
                if(eventBasedItemCodes.contains(item.code)){
                    if(it.level > 30) {
                        !bankService.isInBank(item.code, item.quantity)
                    }else{
                        false
                    }
                }else{ false } } ?: false }
            .filter { item ->
                // Filter out items that require boss monster components not in bank
                item.craft?.items?.none { ingredient ->
                    val monster = monsterService.findMonsterThatDrop(ingredient.code)
                    if(monster?.type == "boss"){
                        !bankService.isInBank(ingredient.code, ingredient.quantity)
                    }else{ false }
                } ?: true
            }
            .sortedBy { it.level }
    }

    /**
     * Saves a new crafted item or updates its quantity if it already exists
     */
    private fun saveOrUpdateCraftedItem(itemDetail: ItemDetails) {
        val existingItem = craftedItemRepository.findById(itemDetail.code)
        if (existingItem.isPresent) {
            val updatedItem = existingItem.get().copy(quantity = existingItem.get().quantity + 1)
            craftedItemRepository.save(updatedItem)
        } else {
            craftedItemRepository.save(CraftedItemDocument.fromItemDetails(itemDetail, 1))
        }
    }

    private fun getTopLowestCostingItemForLeveling(level: Int, skills : List<String>) : ItemDetails{
        val minLevel = level -10
        val items = itemService.getCrafterItemsBetweenLevel(minLevel-1, level +1, skills)
        val itemCostMatrix = HashMap<ItemDetails, Int>()
        // Exclude very hard items and the tutorial one
        items.filter { it.code != "wooden_staff"  }
            .filter {
                it.craft?.items?.none { item ->
                    if(rareItemCode.contains(item.code)){
                        !bankService.isInBank(item.code, item.quantity) // Just false ?
                    }else{ false } } ?: false }
            .filter { it.craft?.items?.none { item ->
                if(eventBasedItemCodes.contains(item.code)){
                    !bankService.isInBank(item.code, item.quantity)
                }else{ false } } ?: false }
            .forEach {
            itemCostMatrix.put(
                it,
                itemService.getWeightToCraft(it))
        }
        return itemCostMatrix.minWith(compareBy { it.value }).key
    }

    private fun cleanUpBank(): ArtifactsCharacter {
        character = movementService.moveToBank(character)
        character = bankService.emptyInventory(character)
        var nbItems = 10
        val itemsToRecycle = arrayListOf<ItemDetails>().toMutableList()
        val minCrafterLevel = min(character.weaponcraftingLevel, min( character.gearcraftingLevel, character.jewelrycraftingLevel)) -10
        bankService.getAllEquipmentsUnderLevel(minCrafterLevel)
            .map { BankItemDocument.toItemDetails(it) }
            .forEach {
                // If it's not not, not the tutorial weapon and it's a craftable item, recycle it
            if(nbItems>0 && it != null && it.code != "wooden_staff" && it.craft != null){
                character = bankService.withdrawAllOfOne(character, it.code)

                itemsToRecycle.add(it)
                nbItems--
            }
                // If it's the tutorial item, destroy it
            if(nbItems>0 && it != null && it.code == "wooden_staff"){
                character = characterService.destroyAllOfOne(character, it.code)
            }
            // If it's a dropped item, it'll be taken care of when an event happens
        }
        if(itemsToRecycle.isNotEmpty()){
            itemsToRecycle.forEach {
                character = gatheringService.recycle(character, it, 1)
            }
        }
        character = movementService.moveToBank(character)
        return bankService.emptyInventory(character)
    }

    fun claimPendingItems() : ArtifactsCharacter{
        val pending = accountClient.getPendingItems().data
        if(pending.isNotEmpty()){
            for (item in pending) {
                character = characterService.claimPendingItem(character, item)
            }
        }
        character = movementService.moveToBank(character)
        character = bankService.emptyInventory(character)
        return character
    }
}
