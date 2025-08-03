package com.tellenn.artifacts.jobs

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
    applicationContext: ApplicationContext,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    private val taskService: TaskService,
    private val equipmentService: EquipmentService,
    private val battleService: BattleService,
    private val monsterService: MonsterService
) : GenericJob(mapService, applicationContext, movementService, bankService, characterService) {

    lateinit var character: ArtifactsCharacter

    fun run(initCharacter: ArtifactsCharacter) {
        character = init(initCharacter)
        do{

            try {
                character = taskService.getNewMonsterTask(character)
                character = taskService.doCharacterTask(character)
                character = bankService.emptyInventory(character)
                if(bankService.isInBank("tasks_coin",16)) {
                    character = taskService.exchangeRewardFromBank(character)
                }
            }catch (e: TaskFailedException){
                if(bankService.isInBank("tasks_coin",1)) {
                    character = taskService.abandonMonsterTask(character)
                }else{
                    character = equipmentService.equipBestAvailableEquipmentForMonsterInBank(character, "red_slime")
                    val monsterMap = monsterService.findMonsterMap("red_slime")
                    character = movementService.moveCharacterToCell(monsterMap.x, monsterMap.y, character)
                    character = battleService.battleUntilInvIsFull(character)
                    character = bankService.emptyInventory(character)
                }
            }
        }while (true)

    }
}
