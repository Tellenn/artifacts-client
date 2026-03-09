package com.tellenn.artifacts.controller

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.responses.SimulationResult
import com.tellenn.artifacts.services.EquipmentService
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class BattleSimulatorController(
    private val battleSimulatorService: BattleSimulatorService,
    private val accountClient: AccountClient,
    private val equipmentService: EquipmentService
) {

    @PostMapping("/simulate/{characterName}")
    fun simulateWithCharacter(
        @RequestParam monsterCode: String,
        @PathVariable characterName: String,
        @RequestParam useBankItems: Boolean = false
    ): SimulationResult {
        if(useBankItems){
            val character = accountClient.getCharacter(characterName).data
            val bis = equipmentService.findBestEquipmentForMonsterInBank(character, monsterCode)
            bis.forEach { slot, item ->
                if(item != null){
                    character["${slot}_slot"] = item.code
                }
            }
            return battleSimulatorService.simulateWithApi(monsterCode, character).data
        }else {
            return battleSimulatorService.simulateWithCharacterName(monsterCode, characterName).data
        }
    }
}
