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
import com.tellenn.artifacts.services.CraftLevelingService
import com.tellenn.artifacts.services.EventService
import com.tellenn.artifacts.services.GatheringService
import com.tellenn.artifacts.services.LevelingChoice
import com.tellenn.artifacts.services.ItemService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.AchievementService
import com.tellenn.artifacts.services.MonsterService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.RaidService
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
    private val raidService: RaidService,
    private val achievementService: AchievementService,
    private val uniqueArtifactService: UniqueArtifactService,
    private val contextService: CharacterContextService,
    private val craftLevelingService: CraftLevelingService,
) : GenericJob(mapService, movementService, bankService, characterService, accountClient, taskService) {

    lateinit var character: ArtifactsCharacter
    val rareItemCode = listOf("magical_cure", "jasper_crystal", "astralyte_crystal", "enchanted_fabric", "ruby", "sapphire", "emerald", "topaz", "diamond")
    var eventBasedItemCodes = listOf<String>()
    var raidRewardItemCodes = setOf<String>()

    // Nombre d'échecs consécutifs du leveling sur inventaire plein (497). Sert de garde-fou : sans lui,
    // le do-while re-tente le cycle complet sans délai et sature le quota API (2000 req/h).
    private var consecutiveInventoryFullFailures = 0

    fun run(characterName: String) {
        sleep(1000)
        eventBasedItemCodes = eventService.getAllEventMaterials()
        raidRewardItemCodes = raidService.getAllRaidRewardItemCodes()
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
            val skillToLevel = craftLevelingService.selectSkillToLevel(
                character, listOf("weaponcrafting", "gearcrafting", "jewelrycrafting")
            )
            if (skillToLevel == null) {
                // Aucune recette « sans matériau rare » ni couverte par un surplus : on protège la
                // réserve et on laisse la boucle principale faire autre chose (nettoyage, crafts
                // banque, collecte) plutôt que d'entamer les matériaux rares.
                continue
            }
            val oldLevel = character.getLevelOf(skillToLevel)
            while (oldLevel == character.getLevelOf(skillToLevel)) {
                // Re-sélection avant chaque craft : un craft consomme exactement la quantité requise,
                // donc tant qu'un surplus existe la banque reste au-dessus du plancher ; dès qu'il
                // est épuisé, selectLevelingCraft renvoie NoViableRecipe et on s'arrête.
                val choice = craftLevelingService.selectLevelingCraft(character, skillToLevel)
                if (choice !is LevelingChoice.Craft) break
                val itemToCraft = choice.item
                contextService.setObjective(character.name, "Craft de ${itemToCraft.code} ×${choice.batchSize} pour level up $skillToLevel (niv. $oldLevel)")
                try {
                    log.info("{} is gathering and crafting {} x{} for leveling", character.name, itemToCraft.code, choice.batchSize)
                    character = gatheringService.craftAndRecycleForLeveling(character, itemToCraft, choice.batchSize, allowFight = true)
                    consecutiveInventoryFullFailures = 0
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
                }catch (_: CharacterInventoryFullException) {
                    log.warn("Character inventory is full, something went terribly wrong")
                    character = accountClient.getCharacter(character.name).data
                    character = movementService.moveToBank(character)
                    character = bankService.emptyInventory(character)
                    // Backoff exponentiel : un 497 récurrent sur le leveling ne doit pas re-boucler le
                    // cycle complet sans délai et cramer le quota API. On temporise avant de rendre la main.
                    consecutiveInventoryFullFailures++
                    val backoff = crafterInventoryFullBackoffMillis(consecutiveInventoryFullFailures)
                    if (backoff > 0) {
                        log.warn("{} : {} échec(s) inventaire plein consécutif(s) sur le leveling — pause de {} ms",
                            character.name, consecutiveInventoryFullFailures, backoff)
                        sleep(backoff)
                    }
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
                            "tasks_coin",
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
                // Filter out items that require boss monster components not in bank.
                // A non-craftable item (craft == null) is never a valid craft target → excluded.
                item.craft?.items?.none { ingredient ->
                    val monster = monsterService.findMonsterThatDrop(ingredient.code)
                    if(monster?.type == "boss"){
                        !bankService.isInBank(ingredient.code, ingredient.quantity)
                    }else{ false }
                } ?: false
            }
            .filter { item ->
                // Filter out items requiring a raid-only reward component not in bank: raids run on a
                // schedule and cannot be triggered by the bot, so such a craft would stay blocked.
                // A non-craftable item (craft == null) is never a valid craft target → excluded.
                item.craft?.items?.none { ingredient ->
                    if(raidRewardItemCodes.contains(ingredient.code)){
                        !bankService.isInBank(ingredient.code, ingredient.quantity)
                    }else{ false }
                } ?: false
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

    fun cleanUpBank(): ArtifactsCharacter {
        character = movementService.moveToBank(character)
        character = bankService.emptyInventory(character)
        val minCrafterLevel = min(character.weaponcraftingLevel, min(character.gearcraftingLevel, character.jewelrycraftingLevel)) - 10
        bankService.getAllEquipmentsUnderLevel(minCrafterLevel)
            .mapNotNull { BankItemDocument.toItemDetails(it) }
            .forEach { item ->
                when {
                    // Tutorial weapon: destroyed rather than recycled.
                    item.code == "wooden_stick" -> {
                        character = characterService.destroyAllOfOne(character, item.code)
                    }
                    // Craftable equipment: recycled by steps to keep the inventory bounded.
                    item.craft != null -> {
                        character = recycleWholeStockInSteps(item)
                    }
                    // Otherwise a dropped, non-craftable item: handled when an event happens.
                }
            }
        character = movementService.moveToBank(character)
        return bankService.emptyInventory(character)
    }

    /**
     * Recycle tout le stock banque de [item] par étapes tenant dans l'inventaire, en le vidant après
     * chaque étape. Retirer tout le stock d'un coup (ou cumuler plusieurs équipements avant de
     * recycler) débordait l'inventaire → 497 en boucle « prend puis repose ». Chaque recyclage rend
     * des matériaux qui occupent eux aussi l'inventaire : le chunk est borné par [recycleChunkSize].
     */
    private fun recycleWholeStockInSteps(item: ItemDetails): ArtifactsCharacter {
        val ingredientCount = item.craft?.items?.sumOf { it.quantity } ?: return character
        val perStep = recycleChunkSize(ingredientCount, character.inventoryMaxItems, RECYCLE_INVENTORY_SAFE_MARGIN)
        var remaining = bankService.getOne(item.code).quantity
        while (remaining > 0) {
            val step = min(perStep, remaining)
            contextService.setStep(character.name, "recyclage de ${step}× ${item.code} ($remaining restants)")
            character = movementService.moveToBank(character)
            character = bankService.withdrawOne(item.code, step, character)
            character = gatheringService.recycle(character, item, step, true)
            character = movementService.moveToBank(character)
            character = bankService.emptyInventory(character)
            remaining -= step
        }
        return character
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

private const val INVENTORY_FULL_BACKOFF_BASE_MS = 1000L
private const val INVENTORY_FULL_BACKOFF_MAX_MS = 60_000L

/** Marge de sécurité (en items) conservée lors du recyclage par étapes du nettoyage de banque. */
private const val RECYCLE_INVENTORY_SAFE_MARGIN = 5

/**
 * Nombre de pièces d'équipement à recycler par étape sans déborder l'inventaire. Un recyclage rend
 * jusqu'à [recipeIngredientCount] matériaux par pièce ; le chunk est donc borné pour que la totalité
 * des matériaux récupérés tienne dans la capacité disponible (`inventoryMaxItems - safeMargin`),
 * au minimum une pièce à la fois.
 */
internal fun recycleChunkSize(recipeIngredientCount: Int, inventoryMaxItems: Int, safeMargin: Int = 0): Int {
    val capacity = (inventoryMaxItems - safeMargin).coerceAtLeast(1)
    return if (recipeIngredientCount <= 1) capacity else maxOf(1, capacity / recipeIngredientCount)
}

/**
 * Temps d'attente (ms) avant de re-tenter le leveling après [consecutiveFailures] échecs consécutifs
 * sur inventaire plein (497). Backoff exponentiel (base 1 s, ×2 par échec) plafonné à 60 s, pour
 * qu'une boucle d'erreur ne sature jamais le quota API. Zéro échec = aucune attente.
 */
internal fun crafterInventoryFullBackoffMillis(consecutiveFailures: Int): Long {
    if (consecutiveFailures <= 0) return 0L
    // Décalage borné avant plafonnement pour éviter tout dépassement de Long.
    val exponent = (consecutiveFailures - 1).coerceAtMost(20)
    return (INVENTORY_FULL_BACKOFF_BASE_MS shl exponent).coerceAtMost(INVENTORY_FULL_BACKOFF_MAX_MS)
}
