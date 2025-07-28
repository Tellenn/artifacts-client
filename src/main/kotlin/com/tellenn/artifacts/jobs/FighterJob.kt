package com.tellenn.artifacts.jobs

import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.config.CharacterConfig
import com.tellenn.artifacts.services.BankService
import com.tellenn.artifacts.services.BattleService
import com.tellenn.artifacts.services.CharacterService
import com.tellenn.artifacts.services.EquipmentService
import com.tellenn.artifacts.services.MapProximityService
import com.tellenn.artifacts.services.MonsterService
import com.tellenn.artifacts.services.MovementService
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * Job implementation for characters with the "fighter" job.
 */
@Component
class FighterJob(
    mapProximityService: MapProximityService,
    applicationContext: ApplicationContext,
    movementService: MovementService,
    bankService: BankService,
    characterService: CharacterService,
    private val equipmentService: EquipmentService,
    private val monsterService: MonsterService,
    private val battleService: BattleService
) : GenericJob(mapProximityService, applicationContext, movementService, bankService, characterService) {

    lateinit var character: ArtifactsCharacter

    fun run(initCharacter: ArtifactsCharacter) {
        character = init(initCharacter)
        do{
            character = equipmentService.equipBestAvailableEquipmentForMonsterInBank(character, "chicken")
            val monsterMap = monsterService.findMonsterMap("chicken")
            character = movementService.moveCharacterToCell(monsterMap.x, monsterMap.y, character)
            character = battleService.battleUntilInvIsFull(character)
            character = bankService.emptyInventory(character)
        }while (true)

    }
}
