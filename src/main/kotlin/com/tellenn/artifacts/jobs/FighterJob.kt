package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.exceptions.TaskFailedException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.BattleService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.EquipmentService
import com.tellenn.artifacts.services.MapService
import com.tellenn.artifacts.services.MonsterService
import com.tellenn.artifacts.services.MovementService
import com.tellenn.artifacts.services.TaskService
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * Job implementation for characters with the "fighter" job.
 */
@Component
class FighterJob(
    mapService: MapService,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    accountClient: AccountClient,
    taskService: TaskService,
    private val battleService: BattleService,
    private val equipmentService: EquipmentService,
    private val monsterService: MonsterService
) : GenericJob(mapService, movementService, bankService, characterService, accountClient, taskService) {

    lateinit var character: ArtifactsCharacter

    fun run(characterName: String) {
        character = init(characterName)
        do{

            try {
                character = taskService.getNewMonsterTask(character)
                log.info("${character.name} is doing a new monster task")
                character = taskService.doCharacterTask(character)
                character = bankService.emptyInventory(character)
                if(bankService.isInBank("tasks_coin",16)) {
                    character = taskService.exchangeRewardFromBank(character)
                }
            }catch (e: TaskFailedException){
                log.info("The task is too hard for ${character.name}, he's switching out")
                character = accountClient.getCharacter(characterName).data
                if(bankService.isInBank("tasks_coin",1)) {
                    character = taskService.abandonMonsterTask(character)
                }else{
                    character = equipmentService.equipBestAvailableEquipmentForMonsterInBank(character, "red_slime")
                    val monsterMap = monsterService.findMonsterMap("red_slime")
                    character = movementService.moveCharacterToCell(monsterMap.x, monsterMap.y, character)
                    character = battleService.battleUntilInvIsFull(character, "red_slime")
                    character = bankService.emptyInventory(character)
                }
            }
        }while (true)

    }
}
