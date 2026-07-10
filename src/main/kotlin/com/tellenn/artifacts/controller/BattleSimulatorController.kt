package com.tellenn.artifacts.controller

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.responses.SimulationResult
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.EquipmentService
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import com.tellenn.artifacts.services.battlesim.model.LocalSimulationResult
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

    /**
     * Simulation déléguée à l'API du jeu (`/simulation/fight`, ~1 req/s).
     */
    @PostMapping("/simulate/{characterName}")
    fun simulateWithCharacter(
        @RequestParam monsterCode: String,
        @PathVariable characterName: String,
        @RequestParam useBankItems: Boolean = false
    ): SimulationResult {
        val character = resolveCharacter(characterName, monsterCode, useBankItems)
        return battleSimulatorService.simulateWithApi(monsterCode, character).data
    }

    /**
     * Simulation Monte-Carlo locale (hors ligne, sans appel réseau ni quota API) : le front peut
     * enchaîner autant d'appels que voulu. Les logs de combat détaillés sont exclus par défaut
     * (`includeLogs`) pour garder la réponse légère.
     */
    @PostMapping("/simulate/local/{characterName}")
    fun simulateLocally(
        @RequestParam monsterCode: String,
        @PathVariable characterName: String,
        @RequestParam(defaultValue = "100") runs: Int,
        @RequestParam(required = false) seed: Long?,
        @RequestParam(defaultValue = "false") useBankItems: Boolean,
        @RequestParam(defaultValue = "false") includeLogs: Boolean
    ): LocalSimulationResult {
        val character = resolveCharacter(characterName, monsterCode, useBankItems)
        val result = battleSimulatorService.simulateLocally(monsterCode, character, runs, seed)
        return if (includeLogs) result else result.withoutLogs()
    }

    private fun resolveCharacter(
        characterName: String,
        monsterCode: String,
        useBankItems: Boolean
    ): ArtifactsCharacter {
        val character = accountClient.getCharacter(characterName).data
        if (useBankItems) {
            equipmentService.findBestEquipmentForMonsterInBank(character, monsterCode).forEach { (slot, item) ->
                if (item != null) character["${slot}_slot"] = item.code
            }
        }
        return character
    }

    private fun LocalSimulationResult.withoutLogs(): LocalSimulationResult =
        copy(results = results.map { it.copy(logs = emptyList()) })
}