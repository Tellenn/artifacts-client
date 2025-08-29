package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.AppConfig.maxLevel
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.BattleService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.GatheringService
import com.tellenn.artifacts.services.ItemService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.MerchantService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.ResourceService
import com.tellenn.artifacts.services.TaskService
import jdk.jshell.spi.ExecutionControl
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.lang.Thread.sleep
import kotlin.math.max

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
    private val itemService: ItemService,
    private val itemRepository: ItemRepository,
    private val merchantService: MerchantService,
) : GenericJob(mapService, movementService, bankService, characterService, accountClient, taskService) {

    lateinit var character: ArtifactsCharacter
    val skill = "alchemy"

    // TODO : Healing potion management
    // TODO : Buff potion management
    // TODO : Teleportation potion management
    // TODO : dynamic assigned work
    // TODO : Improve and use the battlesim
    fun run(characterName: String) {
        sleep(2000)
        character = init(characterName)
        do{
            character = catchBackCrafter(character)
            cookEasyItemsInBank()
            if(character.alchemyLevel == maxLevel && character.cookingLevel == maxLevel){
                log.info("${character.name} is doing a new itemTask")
                character = taskService.getNewItemTask(character)
                character = taskService.doCharacterTask(character)

            }else if(character.alchemyLevel < character.fishingLevel){
                val itemsToCraft = ArrayList<SimpleItem>()
                getHealingPotions().forEach {
                    if(!bankService.isInBank(it.code, 400)){
                        itemsToCraft.add(SimpleItem(it.code, (character.inventoryMaxItems - 10) / itemService.getInvSizeToCraft(it) ))
                    }
                }
                if(character.alchemyLevel >= 20 && !bankService.isInBank("small_antidote", 400)){
                    itemsToCraft.add(SimpleItem("small_antidote", (character.inventoryMaxItems - 10) / itemService.getInvSizeToCraft(itemService.getItem("small_antidote")) ))
                }

                // Do some stock for the crafter
                if(itemsToCraft.isNotEmpty()){
                    itemsToCraft.forEach {
                        log.info("${character.name} is crafting ${it.code} for stocks")
                        if(it.code == "greater_health_potion" && character.alchemyLevel < itemService.getItem("greater_health_potion").level + 5 ){
                            log.debug("We need to level up our alchemy to ${itemService.getItem("greater_health_potion").level + 5}")
                        }else{
                            character = gatheringService.craftOrGather(character, it.code, it.quantity)
                            character = bankService.emptyInventory(character)
                        }
                    }
                    continue
                    // Otherwise levelup
                }else if(character.alchemyLevel < maxLevel){
                    val item =
                        itemService.getBestCraftableItemsBySkillAndSubtypeAndMaxLevel(skill, "potion", character.alchemyLevel)
                    if (item == null) {
                        throw Exception("No craftable item found")
                    }
                    log.info("${character.name} is crafting ${item.code} to level up their alchemy")
                    character = gatheringService.craftOrGather(
                        character = character,
                        itemCode = item.code,
                        quantity = (character.inventoryMaxItems -10 )/ itemService.getInvSizeToCraft(item),
                        allowFight = true

                    )
                    character = bankService.emptyInventory(character)
                    continue
                    // Or do some tasks to get task coins
                }
            }else{
                val itemsToCraft = getBestFishBasedFood()
                log.info("${character.name} is crafting ${itemsToCraft.code} to level up their fishing / cooking")
                character = gatheringService.craftOrGather(character, itemsToCraft.code, character.inventoryMaxItems - 20)
            }
        }while(true)
    }

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
            .maxBy { it.level }
    }

    private fun cookEasyItemsInBank(){
         bankService.getAllResources().forEach {
            val craftableItems = itemService.getItemsCraftedBySkillAndItemUnderLevel(it.code, "cooking", character.cookingLevel)
            when(craftableItems.size){
                0 -> log.debug("Error when analysing ${it.name}, it's a craftable item that does not have crafts ?")
                1 -> {
                    val craftableItem = craftableItems.first()
                    if(craftableItem.craft?.items?.size == 1) {
                        character = gatheringService.craftOrGather(character, craftableItem.code, max(character.inventoryMaxItems - 20, it.quantity) / craftableItem.craft.items[0].quantity)
                    }else{
                        log.debug("We have 1 craft, but it requires other items, unsure what to do so we abort")
                    }
                }
                else -> log.debug("There is more than 1 craft, so we need further analysis")
            }
        }
    }
}
