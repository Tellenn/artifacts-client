package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.AppConfig.maxLevel
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.exceptions.MapContentNotFoundException
import com.tellenn.artifacts.exceptions.MissingItemException
import com.tellenn.artifacts.exceptions.NoCraftableItemException
import com.tellenn.artifacts.models.ArtifactsCharacter
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
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.springframework.stereotype.Component
import java.lang.Thread.sleep
import kotlin.text.isNullOrEmpty

/**
 * Job implementation for characters with the "woodworker" job.
 */
@Component
class WoodworkerJob(
    mapService: MapService,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    accountClient: AccountClient,
    taskService: TaskService,
    val gatheringService: GatheringService,
    private val gatheringWorkerService: GatheringWorkerService,
    val itemService: ItemService,
    val bankItemSyncService: BankItemSyncService,
    val achievementService: AchievementService,
    val contextService: CharacterContextService,
) : GenericJob(mapService, movementService, bankService, characterService, accountClient, taskService) {

    lateinit var character: ArtifactsCharacter
    val skill: String = "woodcutting"

    fun run(characterName: String) {
        sleep(4000)
        character = init(characterName)
        character = craftBasicMaterialFromBank(skill, "plank", itemService, gatheringService, bankItemSyncService, character)


        do{
            if (isCrafterMaxLevel()) {
                contextService.setObjective(characterName, "Exécution des achievements (crafter max)")
                character = achievementService.executeAchievement(character, "woodworker")
                continue
            }
            if (gatheringWorkerService.hasOpenTasks(listOf(skill), mapOf(skill to character.woodcuttingLevel))) {
                contextService.setObjective(characterName, "Production pour le pool du crafter")
                val poolResult = gatheringWorkerService.workOpenTasks(
                    character, listOf(skill), mapOf(skill to character.woodcuttingLevel)
                )
                character = poolResult.character
                if (poolResult.produced > 0) continue
            }
            contextService.setObjective(characterName, "Alignement de niveau avec le crafter")
            character = catchBackCrafter(character)
            val itemsToCraft = ArrayList<SimpleItem>()
            val gatheringItems = itemService.getAllCraftBySkillUnderLevel(skill, character.woodcuttingLevel)
                .filter { it.subtype != "precious_stone" }
                .filter { it.code != "cursed_plank" && it.code != "magical_plank" && it.code != "magic_sap"}
            for(it in gatheringItems){
                if(!bankService.isInBank(it.code, 100)){
                    itemsToCraft.add(SimpleItem(it.code, (character.inventoryMaxItems - 20) / itemService.getInvSizeToCraft(it) ))
                    break
                }
            }

            // Do some stock for the crafter
            if(itemsToCraft.isNotEmpty()){
                try {
                    for (it in itemsToCraft) {
                        log.info("${character.name} is stocking up on some ${it.code}")
                        contextService.setObjective(characterName, "Stock de ${it.code} pour le crafter (cible : 200)")
                        character = gatheringService.craftOrGather(character, it.code, it.quantity)
                        character = movementService.moveToBank(character)
                        character = bankService.emptyInventory(character)
                    }
                }catch (e: MapContentNotFoundException){
                    character = accountClient.getCharacter(character.name).data
                    log.error("Tried to gather something that wasn't there. Investigate why?", e)
                }catch (e: MissingItemException){
                    character = accountClient.getCharacter(character.name).data
                    log.error("Tried to craft while not having enought items ... it's a bother",e)
                }
                continue
                // Otherwise levelup
            }else if(character.woodcuttingLevel < maxLevel){
                log.info("${character.name} is leveling his woodcutting skill")
                val items =
                    itemService.getAllCraftableItemsBySkillAndSubtypeAndMaxLevel(skill, "plank", character.woodcuttingLevel)
                        .filter { it.code != "cursed_plank" && it.code != "magical_plank" }
                if (items.isEmpty()) {
                    throw NoCraftableItemException(skill, character.woodcuttingLevel)
                }
                val item = items.first()
                contextService.setObjective(characterName, "Level up bûcheronnage → craft de ${item.code} (niv. ${character.woodcuttingLevel})")
                character = gatheringService.craftOrGather(
                    character,
                    item.code,
                    (character.inventoryMaxItems -10 )/ itemService.getInvSizeToCraft(item)
                )
                character = movementService.moveToBank(character)
                character = bankService.emptyInventory(character)

                // Or do some tasks to get task coins
            }else{
                log.info("${character.name} is doing a new itemTask")
                contextService.setObjective(characterName, "Tâche d'item (niv. max atteint)")
                if(character.task.isNullOrEmpty()){
                    character = taskService.getNewItemTask(character)
                }
                character = taskService.doCharacterTask(character)
            }
        }while(true)
    }
}
