package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CraftingClient
import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.exceptions.BattleLostException
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
    private val grandExchangeService: GrandExchangeService,
    private val gatheringTaskService: GatheringTaskService,
    private val materialResponsibility: MaterialResponsibility,
    private val characterContextService: CharacterContextService,
) {
    private val log = LogManager.getLogger(GatheringService::class.java)

    companion object {
        private const val INVENTORY_SAFE_MARGIN = 5
        private const val ENHANCED_RECYCLE_MIN_LEVEL = 20
        private const val ENHANCED_RECYCLE_MIN_BANK_GOLD = 20000
        private val GATHERING_SKILLS = setOf("mining", "woodcutting", "fishing", "alchemy")
    }

    /*
     * The goal of this function is to be able to craft any item with any complexity as long as it's possible within a single inventory
     * It can gather, craft, fight, buy in order to be able to craft
     * The goal is to have everything available in the bank, and then fetch everything from the bank
     */
    fun craftOrGather(character: ArtifactsCharacter, itemCode: String, quantity: Int, functionLevel: Int = 0, allowFight: Boolean = false, shouldTrain: Boolean = true) : ArtifactsCharacter{
        if (functionLevel == 0) {
            // Une recette interrompue laisserait sinon des réservations fantômes qui rendent le
            // stock banque invisible pour tous les personnages (spirale de re-collecte).
            return try {
                assertRecipeObtainable(character, itemService.getItem(itemCode), quantity, shouldTrain)
                resolveCraftOrGather(character, itemCode, quantity, functionLevel, allowFight, shouldTrain)
            } catch (e: Exception) {
                bankService.releaseAllReservations(character.name)
                throw e
            } finally {
                // Symétrique aux réservations : une étape fantôme ne doit pas survivre au craft.
                characterContextService.clearStep(character.name)
            }
        }
        return resolveCraftOrGather(character, itemCode, quantity, functionLevel, allowFight, shouldTrain)
    }

    /**
     * Parcourt la recette complète avant toute collecte et échoue immédiatement si un composant
     * manquant est hors de portée : combat perdu d'avance, niveau de récolte ou de craft
     * insuffisant. Sans ce garde-fou, le blocage n'est découvert qu'en arrivant au composant
     * fautif — après avoir déjà collecté les précédents pour rien.
     * Les composants déjà couverts par la banque n'ont rien à prouver ; les personnages en mode
     * entraînement ([shouldTrain]) assument leurs défaites, leur combat n'est donc pas simulé.
     */
    private fun assertRecipeObtainable(character: ArtifactsCharacter, itemDetails: ItemDetails, quantity: Int, shouldTrain: Boolean) {
        if (bankService.availableQuantity(itemDetails.code) >= quantity) {
            return
        }
        val craft = itemDetails.craft
        when {
            itemDetails.subtype == "mob" -> {
                if (!shouldTrain && !battleService.isFightForItemWinnable(character, itemDetails.code)) {
                    throw BattleLostException(itemDetails.code)
                }
            }
            craft != null -> {
                if (character.getLevelOf(craft.skill) < craft.level) {
                    throw CharacterSkillTooLow("Insufficient level to craft ${itemDetails.code}", craft.skill, character.getLevelOf(craft.skill))
                }
                craft.items.forEach { ingredient ->
                    assertRecipeObtainable(character, itemService.getItem(ingredient.code), ingredient.quantity * quantity, shouldTrain)
                }
            }
            itemDetails.subtype in GATHERING_SKILLS -> {
                if (itemDetails.level > character.getLevelOf(itemDetails.subtype)) {
                    throw CharacterSkillTooLow("Insufficient level to gather ${itemDetails.code}", itemDetails.subtype, character.getLevelOf(itemDetails.subtype))
                }
            }
        }
    }

    private fun resolveCraftOrGather(character: ArtifactsCharacter, itemCode: String, quantity: Int, functionLevel: Int, allowFight: Boolean, shouldTrain: Boolean) : ArtifactsCharacter{

        val itemDetails = itemService.getItem(itemCode)
        val sizeForOne = itemService.getInvSizeToCraft(itemDetails)
        val inventorySizeNeeded = quantity * sizeForOne
        require( quantity >0 && inventorySizeNeeded <= character.inventoryMaxItems){"Cannot craft or gather $quantity item with code $itemCode because the inventory is too small"}

        var remaining = quantity
        if(functionLevel > 0) {
            // Sub item: reserve what the bank already covers so other agents can't claim it,
            // and only produce the missing part instead of re-gathering the full amount.
            val fromBank = minOf(bankService.availableQuantity(itemDetails.code), quantity)
            if (fromBank > 0) {
                bankService.reserveInBank(itemDetails.code, fromBank, character.name)
                if (fromBank == quantity) {
                    characterContextService.setStep(character.name, "utilise le stock banque : ${quantity}× ${itemDetails.code}")
                    return character
                }
                characterContextService.setStep(character.name, "stock banque : ${fromBank}× ${itemDetails.code} réservés, produit les ${quantity - fromBank} restants")
            }
            remaining -= fromBank
        }
        return when {
            itemDetails.subtype == "task" -> {
                // It's a task reward item, and we don't have it in stock
                characterContextService.setStep(character.name, "échange task pour ${remaining}× ${itemDetails.code}")
                tradeTaskItem(character, itemDetails, remaining)
            }

            itemDetails.subtype == "mob" -> {
                // It's a monster loot
                characterContextService.setStep(character.name, "combat pour ${remaining}× ${itemDetails.code}")
                fightToGet(character, itemDetails, remaining, allowFight, shouldTrain)
            }

            itemDetails.subtype == "npc" -> {
                // We don't have it, and it's a npc selling it
                characterContextService.setStep(character.name, "achat NPC de ${remaining}× ${itemDetails.code}")
                tradeNpc(character, itemDetails, remaining, functionLevel, allowFight, shouldTrain)
            }

            itemDetails.code == "wooden_stick" -> {
                // Specific for tutorial item
                characterService.unequip(character, "weapon", 1)
            }

            itemDetails.craft == null -> {
                // If there is no craft, check GC before gathering (only for ingredients)
                if (functionLevel > 0 && grandExchangeService.shouldBuyFromGC(character, itemDetails, remaining)) {
                    characterContextService.setStep(character.name, "achat GC de ${remaining}× ${itemDetails.code}")
                    buyFromGC(character, itemDetails, remaining)
                } else {
                    characterContextService.setStep(character.name, "collecte de ${remaining}× ${itemDetails.code}")
                    gather(character, itemDetails, remaining)
                }
            }

            else -> {
                // Check GC before crafting (only for ingredients)
                if (functionLevel > 0 && grandExchangeService.shouldBuyFromGC(character, itemDetails, remaining)) {
                    characterContextService.setStep(character.name, "achat GC de ${remaining}× ${itemDetails.code}")
                    return buyFromGC(character, itemDetails, remaining)
                }

                // Otherwise we craft (and call the same function for it)
                var newCharacter = character
                val itemsToWithdraw = mutableListOf<SimpleItem>()
                for (i in itemDetails.craft.items) {
                    newCharacter =
                        craftOrGather(newCharacter, i.code, i.quantity * remaining, functionLevel + 1, allowFight, shouldTrain)
                    // This is to empty the inventory if we need to stock up on something with a side product.
                    // It avoid the inventoryFullException when fighting mostly
                    newCharacter = movementService.moveToBank(newCharacter)
                    newCharacter = bankService.emptyInventory(newCharacter)
                    itemsToWithdraw.add(SimpleItem(i.code, i.quantity * remaining))
                }
                newCharacter = movementService.moveToBank(newCharacter)
                for (item in itemsToWithdraw) {
                    newCharacter = try {
                        bankService.withdrawOne(item.code, item.quantity, newCharacter)
                    } catch (_: MissingItemException) {
                        // L'item a été retiré par un autre agent entre la réservation et le retrait effectif
                        characterContextService.setStep(character.name, "ingrédient ${item.code} manquant en banque — re-collecte")
                        log.warn("{} : ingrédient {} manquant en banque — re-collecte", newCharacter.name, item.code)
                        bankService.releaseReservation(item.code, item.quantity, newCharacter.name)
                        val reGathered = craftOrGather(newCharacter, item.code, item.quantity, 0, allowFight, shouldTrain)
                        val atBank = movementService.moveToBank(reGathered)
                        val emptied = bankService.emptyInventory(atBank)
                        bankService.withdrawOne(item.code, item.quantity, emptied)
                    }
                }
                // gather() calls emptyInventory() which may have deposited previously obtained ingredients to bank.
                // Re-withdraw any missing ingredients before crafting.
                newCharacter = recollectMissingIngredients(newCharacter, itemDetails.craft.items, remaining)
                characterContextService.setStep(character.name, "assemblage de ${remaining}× ${itemDetails.code}")
                craft(newCharacter, itemDetails, remaining)
            }
        }
    }

    /**
     * Monte une compétence de craft en produisant [quantity] exemplaires de [item] puis en les
     * recyclant, avec une stratégie « tout collecter d'abord » pour minimiser les déplacements :
     *
     * - **Phase 1** ([gatherLevelingMaterials]) : amène en banque la totalité des ingrédients du batch.
     * - **Phase 2** : assemble puis recycle par chunks de la taille de l'inventaire.
     *
     * [craftOrGather] n'est jamais modifié — il est réutilisé tel quel pour produire chaque ingrédient.
     * La méthode lève les mêmes exceptions que [craftOrGather]/[craft] (ex. [CharacterSkillTooLow]) :
     * la récupération reste à la charge de l'appelant.
     */
    fun craftAndRecycleForLeveling(
        character: ArtifactsCharacter,
        item: ItemDetails,
        quantity: Int,
        allowFight: Boolean = false,
    ): ArtifactsCharacter {
        val craft = item.craft ?: return character
        postLevelingShortfalls(item, quantity)
        var newCharacter = gatherLevelingMaterials(character, item, quantity, allowFight)

        // Marge d'inventaire : emptyInventory conserve des potions de téléport, donc l'inventaire
        // n'est jamais réellement vide. Sans marge, retirer inventoryMax items déborde → 497.
        val perChunk = levelingAssembleChunkSize(
            craft.items.sumOf { it.quantity }, newCharacter.inventoryMaxItems, INVENTORY_SAFE_MARGIN
        )
        var remaining = quantity
        while (remaining > 0) {
            val n = minOf(perChunk, remaining)
            newCharacter = movementService.moveToBank(newCharacter)
            for (ingredient in craft.items) {
                newCharacter = bankService.withdrawOne(ingredient.code, ingredient.quantity * n, newCharacter)
            }
            newCharacter = craft(newCharacter, item, n)
            newCharacter = recycle(newCharacter, item, n, forceEnhanced = true)
            newCharacter = movementService.moveToBank(newCharacter)
            newCharacter = bankService.emptyInventory(newCharacter)
            remaining -= n
        }
        return newCharacter
    }

    /**
     * Phase 1 du leveling par batch : amène en banque la totalité des ingrédients directs de [item]
     * pour [quantity] crafts, en deux temps par ingrédient :
     *
     * 1. **Phase pool** — le crafter consomme les tranches ouvertes du pool partagé comme un worker
     *    ([GatheringTaskService.produceOpenSlices]), en concurrence loyale avec les autres personnages.
     * 2. **Phase complément** — ce que le pool et le stock banque ne couvrent pas est collecté
     *    directement, par chunks tenant dans l'inventaire. Si un worker termine sa réservation après
     *    coup, le surplus reste en banque pour le batch suivant.
     *
     * Interne (plutôt que privée) pour être testable unitairement, comme [postLevelingShortfalls].
     */
    internal fun gatherLevelingMaterials(
        character: ArtifactsCharacter,
        item: ItemDetails,
        quantity: Int,
        allowFight: Boolean,
    ): ArtifactsCharacter {
        var newCharacter = character
        for (ingredient in item.craft?.items ?: emptyList()) {
            val unitSize = itemService.getInvSizeToCraft(itemService.getItem(ingredient.code))
            val totalNeeded = ingredient.quantity * quantity
            val chunk = levelingGatherChunkSize(unitSize, totalNeeded, newCharacter.inventoryMaxItems)

            fun gatherChunkToBank(n: Int) {
                newCharacter = craftOrGather(newCharacter, ingredient.code, n, allowFight = allowFight, shouldTrain = false)
                newCharacter = movementService.moveToBank(newCharacter)
                newCharacter = bankService.emptyInventory(newCharacter)
            }

            gatheringTaskService.produceOpenSlices(ingredient.code, newCharacter.name, chunk, ::gatherChunkToBank)

            // availableQuantity (réservations déduites) : le stock réservé par un autre personnage
            // sera retiré par lui — le compter comme disponible sous-estimerait le manque.
            val missing = (totalNeeded - bankService.availableQuantity(ingredient.code)).coerceAtLeast(0)
            var got = 0
            while (got < missing) {
                val n = minOf(chunk, missing - got)
                gatherChunkToBank(n)
                got += n
            }
        }
        return newCharacter
    }

    /**
     * Publie dans le pool partagé les manques de matériaux récoltables d'un batch de leveling, pour
     * que les récolteurs les produisent en parallèle — **uniquement** si au moins deux matériaux
     * délégables manquent. Avec un seul manque délégable, la parallélisation n'apporte rien : le
     * crafter le collecte lui-même (comportement de [gatherLevelingMaterials] inchangé).
     *
     * Publication best-effort : un échec du pool est journalisé et le crafter poursuit sa propre
     * collecte (la publication n'est pas un échec de tâche).
     */
    internal fun postLevelingShortfalls(item: ItemDetails, batchSize: Int) {
        try {
            val craft = item.craft ?: return
            // availableQuantity (réservations déduites) : le stock réservé par un autre personnage
            // n'est pas disponible pour ce batch — le compter sous-estimerait le manque publié.
            val bankQuantities = craft.items.associate { it.code to bankService.availableQuantity(it.code) }
            val shortfalls = levelingShortfalls(item, batchSize, bankQuantities)
            val delegatable = shortfalls.keys.count { materialResponsibility.skillFor(it) != null }
            if (delegatable <= 1) return
            gatheringTaskService.postShortfalls(shortfalls, bankQuantities)
        } catch (e: Exception) {
            log.warn(
                "Échec de publication des manques de matériaux pour {} : {} — le crafter collectera lui-même",
                item.code, e.message
            )
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

    fun recycle(character: ArtifactsCharacter, item: ItemDetails, i: Int, forceEnhanced: Boolean = false): ArtifactsCharacter {
        var newCharacter = character
        val enhancedCost = enhancedRecycleCostOrNull(item, i, forceEnhanced)
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
     * Avec [forceEnhanced] (recyclage du gear de leveling), on ignore les gardes de niveau et de
     * seuil de trésorerie : seule subsiste l'affordabilité (`bankGold >= cost`) — sinon repli propre
     * en recyclage normal plutôt que de tenter un retrait d'or impossible.
     *
     * Coût = somme des quantités d'ingrédients de la recette × quantité recyclée × tarif par ingrédient.
     */
    private fun enhancedRecycleCostOrNull(item: ItemDetails, quantity: Int, forceEnhanced: Boolean = false): Int? {
        val craft = item.craft ?: return null
        if (!forceEnhanced && item.level < ENHANCED_RECYCLE_MIN_LEVEL) return null
        val bankGold = bankService.getBankDetails().gold
        if (!forceEnhanced && bankGold < ENHANCED_RECYCLE_MIN_BANK_GOLD) return null
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
                mapData = mapService.findClosestMapFromApi(character = newCharacter, contentCode = resourceCode, excludeMapIds = excludedMapIds)
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
        val usableCapacity = character.inventoryMaxItems - INVENTORY_SAFE_MARGIN
        require(npcItem.buyPrice <= usableCapacity) {
            "Cannot buy ${item.code}: unit price ${npcItem.buyPrice} $currencyCode exceeds inventory capacity $usableCapacity"
        }
        val currencyQty = npcItem.buyPrice * quantity
        var newCharacter = craftOrGather(character, currencyCode, currencyQty, functionLevel + 1, allowFight, shouldTrain)

        // La devise totale peut dépasser la capacité d'inventaire : un retrait unique serait
        // rejeté en 497 quel que soit l'état de l'inventaire (livelock Renoir du 2026-07-10).
        // On achète donc par passages dimensionnés sur la capacité ; les lots déjà achetés
        // partent en banque au vidage suivant et sont réservés pour n'être volés par personne.
        val itemsPerTrip = usableCapacity / npcItem.buyPrice
        var remaining = quantity
        var boughtAndBanked = 0
        while (remaining > 0) {
            val batch = minOf(remaining, itemsPerTrip)
            val batchCurrency = npcItem.buyPrice * batch
            newCharacter = movementService.moveToBank(newCharacter)
            newCharacter = bankService.emptyInventory(newCharacter)
            newCharacter = try {
                bankService.withdrawOne(currencyCode, batchCurrency, newCharacter)
            } catch (_: MissingItemException) {
                // La devise a été retirée par un autre agent entre la réservation et le retrait effectif
                log.warn("{} : devise {} volée par un autre agent — re-collecte", newCharacter.name, currencyCode)
                bankService.releaseReservation(currencyCode, batchCurrency, newCharacter.name)
                val reGathered = craftOrGather(newCharacter, currencyCode, batchCurrency, 0, allowFight, shouldTrain)
                val atBank = movementService.moveToBank(reGathered)
                val emptied = bankService.emptyInventory(atBank)
                bankService.withdrawOne(currencyCode, batchCurrency, emptied)
            }
            newCharacter = movementService.moveToNpc(newCharacter, npcItem.npc)
            newCharacter = npcClient.buyItem(newCharacter.name, npcItem.code, batch).data.character
            remaining -= batch
            if (remaining > 0) {
                bankService.reserveInBank(item.code, batch, newCharacter.name)
                boughtAndBanked += batch
            }
        }
        if (boughtAndBanked > 0) {
            // Post-condition inchangée : tout l'achat repart avec le personnage en inventaire
            newCharacter = movementService.moveToBank(newCharacter)
            newCharacter = bankService.withdrawOne(item.code, boughtAndBanked, newCharacter)
        }
        return newCharacter
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

/**
 * Nombre d'exemplaires d'un ingrédient à collecter par passage en banque sans déborder l'inventaire.
 * Un footprint ([unitSize]) de 0 désigne un matériau récolté brut : `gather()` gère lui-même ses
 * allers-retours, on demande donc toute la quantité d'un coup.
 */
internal fun levelingGatherChunkSize(unitSize: Int, totalNeeded: Int, inventoryMaxItems: Int): Int =
    if (unitSize <= 0) totalNeeded else maxOf(1, inventoryMaxItems / unitSize)

/**
 * Nombre d'exemplaires de l'item final à assembler par passage : borné par le footprint direct de
 * ses ingrédients ([directSize]) — les sous-crafts sont déjà en banque — au minimum 1.
 */
internal fun levelingAssembleChunkSize(directSize: Int, inventoryMaxItems: Int, safeMargin: Int = 0): Int =
    if (directSize <= 0) 1 else maxOf(1, (inventoryMaxItems - safeMargin) / directSize)

/**
 * Manques de matériaux à publier pour un batch de leveling : pour chaque ingrédient direct de
 * [item], le besoin total (`batchSize × quantité`) moins le stock déjà en banque ([bankQuantities]),
 * en ne gardant que les manques strictement positifs. Arithmétique pure : aucun filtrage par
 * compétence (assuré en aval par `GatheringTaskService.postShortfalls`).
 */
internal fun levelingShortfalls(
    item: ItemDetails,
    batchSize: Int,
    bankQuantities: Map<String, Int>,
): Map<String, Int> {
    val craft = item.craft ?: return emptyMap()
    return craft.items
        .associate { it.code to batchSize * it.quantity - (bankQuantities[it.code] ?: 0) }
        .filterValues { it > 0 }
}
