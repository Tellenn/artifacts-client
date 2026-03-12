package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import org.springframework.stereotype.Service

@Service
class BossFightService(
    private val monsterService: MonsterService,
    private val battleSimulatorService: BattleSimulatorService,
    private val accountClient: AccountClient,
    private val equipmentService: EquipmentService
) {

    companion object {
        const val DEFAULT_CHARACTER_1 = "Kepo"
        const val DEFAULT_CHARACTER_2 = "Renoir"
        const val DEFAULT_CHARACTER_3 = "Cloud"
    }

    /**
     * Simulates a boss fight with the default three characters.
     * Uses default characters: Kepo, Renoir, and Cloud.
     *
     * @param monsterCode The code of the monster to fight
     * @return Simulation result (true if characters would win, false otherwise)
     * @throws IllegalArgumentException if the monster is not a boss type
     */
    fun simulateBossFight(monsterCode: String): Boolean {
        return simulateBossFight(DEFAULT_CHARACTER_1, DEFAULT_CHARACTER_2, DEFAULT_CHARACTER_3, monsterCode)
    }

    /**
     * Simulates a boss fight with three characters.
     * Checks that the monster is a boss type, fetches best gear for each character,
     * and runs a simulation of the fight.
     *
     * @param character1Name First character name
     * @param character2Name Second character name
     * @param character3Name Third character name
     * @param monsterCode The code of the monster to fight
     * @return Simulation result (true if characters would win, false otherwise)
     * @throws IllegalArgumentException if the monster is not a boss type
     */
    fun simulateBossFight(
        character1Name: String,
        character2Name: String,
        character3Name: String,
        monsterCode: String
    ): Boolean {
        // Fetch the monster data
        val monster = monsterService.findMonster(monsterCode)

        // Check that the monster type is boss
        if (monster.type != "boss") {
            throw IllegalArgumentException("Monster $monsterCode is not a boss (type: ${monster.type})")
        }

        // Fetch current character data
        val character1 = accountClient.getCharacter(character1Name).data
        val character2 = accountClient.getCharacter(character2Name).data
        val character3 = accountClient.getCharacter(character3Name).data

        // Get best gear for each character
        val character1WithBestGear = getBestGearForCharacter(character1, monsterCode)
        val character2WithBestGear = getBestGearForCharacter(character2, monsterCode)
        val character3WithBestGear = getBestGearForCharacter(character3, monsterCode)

        // Run the fight simulation
        return runFightSimulation(
            listOf(character1WithBestGear, character2WithBestGear, character3WithBestGear),
            monster
        )
    }

    /**
     * Finds the best gear available in the bank for a character.
     * Currently a placeholder that returns the character as-is.
     */
    private fun getBestGearForCharacter(character: ArtifactsCharacter, monster: String): ArtifactsCharacter {
        val bis = equipmentService.findBestEquipmentForMonsterInBank(character, monster)
        bis.forEach { slot, item ->
            if(item != null){
                character["${slot}_slot"] = item.code
            }
        }
        return character
    }

    /**
     * Simulates a fight between characters and a monster.
     * Currently a placeholder that returns false.
     */
    private fun runFightSimulation(
        characters: List<ArtifactsCharacter>,
        monster: MonsterData
    ): Boolean {
        return battleSimulatorService.simulateWithApi(monster.code, characters).data.winrate == 100
    }

}
