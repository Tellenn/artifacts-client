package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.AppConfig.maxLevel
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.exceptions.MapContentNotFoundException
import com.tellenn.artifacts.exceptions.NoCraftableItemException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.GatheringService
import com.tellenn.artifacts.services.GatheringWorkerService
import com.tellenn.artifacts.services.ItemService
import com.tellenn.artifacts.services.AchievementService
import com.tellenn.artifacts.services.CharacterContextService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.TaskService
import org.springframework.stereotype.Component
import java.lang.Thread.sleep
import kotlin.math.min

/**
 * Job implementation for characters with the "alchemist" job.
 */
@Component
class AlchemistJob(
    mapService: MapService,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    accountClient: AccountClient,
    taskService: TaskService,
    private val gatheringService: GatheringService,
    private val gatheringWorkerService: GatheringWorkerService,
    private val itemService: ItemService,
    private val itemRepository: ItemRepository,
    private val achievementService: AchievementService,
    private val contextService: CharacterContextService,
) : GenericJob(mapService, movementService, bankService, characterService, accountClient, taskService) {

    lateinit var character: ArtifactsCharacter
    val skill = "alchemy"

    fun run(characterName: String) {
        sleep(2000)
        character = init(characterName)
        do{
            if (isCrafterMaxLevel()) {
                contextService.setObjective(characterName, "Exécution des achievements (crafter max)")
                character = achievementService.executeAchievement(character, "alchemist")
                continue
            }
            if (gatheringWorkerService.hasOpenTasks(POOL_SKILLS, poolLevels())) {
                contextService.setObjective(characterName, "Production pour le pool du crafter")
                val poolResult = gatheringWorkerService.workOpenTasks(character, POOL_SKILLS, poolLevels(), allowFight = true)
                character = poolResult.character
                if (poolResult.produced > 0) continue
            }
            contextService.setObjective(characterName, "Alignement de niveau avec le crafter")
            character = catchBackCrafter(character)
            contextService.setObjective(characterName, "Cuisson des ressources disponibles en banque")
            cookEasyItemsInBank()

            if (restockTeleportPotions(characterName)) continue

            if(character.alchemyLevel == maxLevel && character.cookingLevel == maxLevel){
                // If level max, should craft potions for stock before doing tasks.
                var craftedPotions = false
                itemService.getPotions().forEach { potion ->
                    if(!bankService.isInBank(potion.code, 400)){
                        log.info("${character.name} is crafting ${potion.code} for stocks")
                        contextService.setObjective(characterName, "Stock de ${potion.code} pour l'équipe (cible : 400)")
                        character = gatheringService.craftOrGather(character, potion.code, (character.inventoryMaxItems - 40) / itemService.getInvSizeToCraft(itemService.getItem(potion.code)) )
                        character = movementService.moveToBank(character)
                        character = bankService.emptyInventory(character)
                        craftedPotions = true
                    }
                }
                if(craftedPotions){ continue}
                log.info("${character.name} is doing a new itemTask")
                contextService.setObjective(characterName, "Tâche d'item (niv. max atteint)")
                character = taskService.getNewItemTask(character)
                character = taskService.doCharacterTask(character)

            }else if(character.alchemyLevel < character.fishingLevel){
                val itemsToCraft = ArrayList<SimpleItem>()
                if(character.alchemyLevel >= 20 && !bankService.isInBank("small_antidote", 10)){
                    itemsToCraft.add(SimpleItem("small_antidote", (character.inventoryMaxItems - 10) / itemService.getInvSizeToCraft(itemService.getItem("small_antidote")) ))
                }
                val greaterHealthPotionLevel = itemService.getItem("greater_health_potion").level
                getHealingPotions().forEach {
                    val readyToCraft = it.code != "greater_health_potion" || character.alchemyLevel >= greaterHealthPotionLevel + 5
                    if (!bankService.isInBank(it.code, 400) && readyToCraft) {
                        itemsToCraft.add(SimpleItem(it.code, (character.inventoryMaxItems - 10) / itemService.getInvSizeToCraft(it)))
                    } else if (!readyToCraft) {
                        log.debug("${character.name} needs alchemy level ${greaterHealthPotionLevel + 5} to craft ${it.code}, currently ${character.alchemyLevel}")
                    }
                }

                // Do some stock for the crafter
                if(itemsToCraft.isNotEmpty()){
                    itemsToCraft.forEach {
                        log.info("${character.name} is crafting ${it.code} for stocks")
                        contextService.setObjective(characterName, "Stock de potions : ${it.code} (cible : 400)")
                        character = gatheringService.craftOrGather(character, it.code, it.quantity, allowFight = true)
                        character = movementService.moveToBank(character)
                        character = bankService.emptyInventory(character)
                    }
                    continue
                    // Otherwise levelup
                }else if(character.alchemyLevel < maxLevel){
                    if(character.alchemyLevel < 5){
                        contextService.setObjective(characterName, "Récolte de sunflower (alchimie niv. ${character.alchemyLevel})")
                        character = gatheringService.craftOrGather(
                            character = character,
                            itemCode = "sunflower",
                            quantity = (character.inventoryMaxItems -10 ),
                            allowFight = false
                            )
                        continue
                    }
                    // Le meilleur item par niveau peut exiger un ingrédient hors de portée
                    // (ex. antidote → maple_sap, craft woodcutting 40) : on écarte ces recettes
                    // au lieu de laisser CharacterSkillTooLow relancer le thread en boucle.
                    val item = itemService.getAllCraftableItemsBySkillAndSubtypeAndMaxLevel(
                        skill,
                        "potion",
                        character.alchemyLevel
                    ).firstOrNull { candidate ->
                        gatheringService.isRecipeObtainable(character, candidate.code, craftBatchSize(candidate))
                    } ?: throw NoCraftableItemException(skill, character.alchemyLevel)

                    log.info("${character.name} is crafting ${item.code} to level up their alchemy")
                    contextService.setObjective(characterName, "Level up alchimie → craft de ${item.code} (niv. ${character.alchemyLevel})")
                    character = gatheringService.craftOrGather(
                        character = character,
                        itemCode = item.code,
                        quantity = craftBatchSize(item),
                        allowFight = true

                    )
                    character = movementService.moveToBank(character)
                    character = bankService.emptyInventory(character)
                    continue
                    // Or do some tasks to get task coins
                }
            }else{
                try {
                    val itemsToCraft = getBestFishBasedFood()
                    log.info("${character.name} is crafting ${itemsToCraft.code} to level up their fishing / cooking")
                    contextService.setObjective(characterName, "Level up cuisine/pêche → craft de ${itemsToCraft.code} (cooking niv. ${character.cookingLevel})")
                    character =
                        gatheringService.craftOrGather(character, itemsToCraft.code, character.inventoryMaxItems - 30)
                }catch (e: MapContentNotFoundException){
                    log.error("Tried to gather something that wasn't there. Investigate why?", e)
                }
            }
        }while(true)
    }

    private fun craftBatchSize(item: ItemDetails): Int =
        (character.inventoryMaxItems - INVENTORY_MARGIN) / itemService.getInvSizeToCraft(item)

    private fun getHealingPotions(): List<ItemDetails>{
        val potions = itemService.getAllCraftableItemsBySkillAndSubtypeAndMaxLevel("alchemy", "potion", character.alchemyLevel).toMutableList()
        return potions.filter { it.effects?.none { effect -> effect.code != "restore" } ?: false }
    }

    private fun getBestFishBasedFood(): ItemDetails{
        val food = itemService.getAllCraftableItemsBySkillAndSubtypeAndMaxLevel("cooking", "food", character.cookingLevel)
        return food
            .filter { it.effects?.none { effect -> effect.code != "heal" } ?: false }
            .filter { it.craft?.items?.size == 1 && itemRepository.findByCode(it.craft.items[0].code).subtype == "fishing" }
            .filter { it.level <= character.fishingLevel }
            .maxByOrNull { it.level }
            ?: error("${character.name} : invariant violated — no fish-based food found at cooking level ${character.cookingLevel} / fishing level ${character.fishingLevel}")
    }

    private fun restockTeleportPotions(characterName: String): Boolean {
        var craftedSomething = false
        itemService.getCraftableTeleportPotions(character.alchemyLevel).forEach { potion ->
            // On lit le stock via getOne (cache local, sans réservations) : pas besoin de la
            // précision de isInBank ici, et la garde de progression de craftToTarget corrige
            // d'elle-même une lecture périmée au cycle suivant.
            if (bankService.getOne(potion.code).quantity < TELEPORT_STOCK_TRIGGER) {
                contextService.setObjective(
                    characterName,
                    "Stock de potion de téléport : ${potion.code} (cible : $TELEPORT_STOCK_TARGET)"
                )
                try {
                    if (craftToTarget(potion)) craftedSomething = true
                } catch (e: Exception) {
                    log.warn("${character.name} n'a pas pu crafter la potion de téléport ${potion.code} : ${e.message}")
                }
            }
        }
        return craftedSomething
    }

    private fun craftToTarget(potion: ItemDetails): Boolean {
        val perTrip = (character.inventoryMaxItems - INVENTORY_MARGIN) / itemService.getInvSizeToCraft(potion)
        if (perTrip < 1) {
            log.warn("${character.name} : potion ${potion.code} trop coûteuse pour l'inventaire, ignorée")
            return false
        }
        var stock = bankService.getOne(potion.code).quantity
        var craftedAny = false
        while (stock < TELEPORT_STOCK_TARGET) {
            character = gatheringService.craftOrGather(character, potion.code, perTrip, allowFight = false)
            character = movementService.moveToBank(character)
            character = bankService.emptyInventory(character)
            val newStock = bankService.getOne(potion.code).quantity
            if (newStock <= stock) {
                log.warn("${character.name} : stock de ${potion.code} n'a pas progressé ($stock), arrêt")
                return craftedAny
            }
            craftedAny = true
            stock = newStock
        }
        return craftedAny
    }

    private fun cookEasyItemsInBank(){
         bankService.getAllResources().forEach {
            val craftableItems = itemService.getItemsCraftedBySkillAndItemUnderLevel(it.code, "cooking", character.cookingLevel)
            try {
                when (craftableItems.size) {
                    0 -> log.debug("Error when analysing ${it.name}, it's a craftable item that does not have crafts ?")
                    1 -> {
                        val craftableItem = craftableItems.first()
                        if (craftableItem.craft?.items?.size == 1 && bankService.canCraftFromBank(craftableItem, 1)) {
                            character = gatheringService.craftOrGather(
                                character,
                                craftableItem.code,
                                Math.floorDiv(
                                    min(character.inventoryMaxItems - 20, it.quantity),
                                    craftableItem.craft.items[0].quantity
                                )
                            )
                            character = movementService.moveToBank(character)
                            character = bankService.emptyInventory(character)
                        } else {
                            log.debug("We have 1 craft, but it requires other items, unsure what to do so we abort")
                        }
                    }

                    else -> log.debug("There is more than 1 craft, so we need further analysis")
                }
            }catch (_: IllegalArgumentException){
                log.warn("Cannot complete recipe for ${it.name}: incompatible ingredient (mob drop or oversized batch)")
            }
        }
    }

    private fun poolLevels() = mapOf(
        "alchemy" to character.alchemyLevel,
        "fishing" to character.fishingLevel,
        "cooking" to character.cookingLevel,
    )

    companion object {
        private const val TELEPORT_STOCK_TRIGGER = 50
        private const val TELEPORT_STOCK_TARGET = 100
        private const val INVENTORY_MARGIN = 10
        private val POOL_SKILLS = listOf("alchemy", "fishing", "cooking")
    }
}
