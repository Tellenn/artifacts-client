package com.tellenn.artifacts.services

import com.tellenn.artifacts.MainRuntime
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BattleClient
import com.tellenn.artifacts.config.CharacterConfig
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

@Service
class BossFightService(
    private val monsterService: MonsterService,
    private val battleSimulatorService: BattleSimulatorService,
    private val accountClient: AccountClient,
    private val equipmentService: EquipmentService,
    private val movementService: MovementService,
    private val mapService: MapService,
    private val threadService: ThreadService,
    private val battleClient: BattleClient,
    private val characterService: CharacterService,
    private val bankService: BankService,
    private val merchantService: MerchantService
) {

    val log = LogManager.getLogger(MainRuntime::class.java)

    companion object {
        const val DEFAULT_CHARACTER_1 = "Renoir"
        const val DEFAULT_CHARACTER_2 = "Cloud"
        const val DEFAULT_CHARACTER_3 = "Kepo"
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
     * Runs a boss fight with three characters.
     * Uses ThreadService to start missions for each character.
     *
     * @param monsterCode The code of the monster to fight
     * @param character1Name First character name
     * @param character2Name Second character name
     * @param character3Name Third character name
     */
    fun runBossFights(
        monsterCode: String,
        itemCode: String,
        quantity: Int = 20,
        character1Name: String = DEFAULT_CHARACTER_1,
        character2Name: String = DEFAULT_CHARACTER_2,
        character3Name: String = DEFAULT_CHARACTER_3,
    ) : ArtifactsCharacter{
        try {
            val character1Available = !threadService.isCharacterOnMission(DEFAULT_CHARACTER_1)
            val character2Available = !threadService.isCharacterOnMission(DEFAULT_CHARACTER_2)
            val character3Available = !threadService.isCharacterOnMission(DEFAULT_CHARACTER_3)

            if (character1Available && character2Available && character3Available) {
                // Stopping character 2 and 3 threads, they will become slave of character 1
                threadService.stopCharacterThread(character2Name)
                threadService.stopCharacterThread(character3Name)

                var char1 = accountClient.getCharacter(character1Name).data
                var char2 = accountClient.getCharacter(character2Name).data
                var char3 = accountClient.getCharacter(character3Name).data

                char1 = movementService.moveToBank(char1)
                char2 = movementService.moveToBank(char2)
                char3 = movementService.moveToBank(char3)
                // TODO : Do the equipment async, as it queues the gear equipping to each char
                // Treat the char1 as a Tank, so we want him to maximize his threat
                char1 = equipmentService.equipBestAvailableEquipmentForMonsterInBank(
                    character = char1,
                    monsterCode = monsterCode,
                    threatScoreMult = 10
                )
                // Char 2 is a pure dps
                char2 = equipmentService.equipBestAvailableEquipmentForMonsterInBank(char2, monsterCode)
                // Char 3 is a dps as well, but we'll force a healing rune to heal up the tank
                char3 = equipmentService.equipBestAvailableEquipmentForMonsterInBank(char3, monsterCode)
                if (char3.runeSlot != "healing_aura_rune") {
                    // If the char3 is not a healing aura, we'll equip a healing aura
                    if (bankService.isInBank("healing_aura_rune", 1)) {
                        val oldRune = char3.runeSlot
                        char3 = bankService.withdrawOne("healing_aura_rune", 1, char3)
                        char3 = characterService.equip(char3, "healing_aura_rune", "rune", 1)
                        if (oldRune != null) {
                            char3 = bankService.deposit(char3, listOf(SimpleItem(oldRune, 1)))
                        }
                    } else if (bankService.getBankDetails().gold > 25000) {
                        // If we don't have a healing aura, we'll buy one
                        char3 = merchantService.buy("healing_aura_rune", char3)
                        val oldRune = char3.runeSlot
                        char3 = characterService.equip(char3, "healing_aura_rune", "rune", 1)
                        if (oldRune != null) {
                            char3 = movementService.moveToBank(char3)
                            char3 = bankService.deposit(char3, listOf(SimpleItem(oldRune, 1)))
                        }
                    }
                }
                val map = mapService.findClosestMap(char1, contentCode = monsterCode)

                char1 = movementService.moveCharacterToCell(map.mapId, char1)
                char2 = movementService.moveCharacterToCell(map.mapId, char2)
                char3 = movementService.moveCharacterToCell(map.mapId, char3)
                var done = 0
                do {
                    char1 = characterService.rest(char1)
                    char2 = characterService.rest(char2)
                    char3 = characterService.rest(char3)

                    val chars = battleClient.fightBoss(char1.name, char2.name, char3.name)
                    chars.data.characters.forEach {
                        when (it.name) {
                            character1Name -> char1 = it
                            character2Name -> char2 = it
                            character3Name -> char3 = it
                        }
                    }
                    chars.data.fight?.characters?.forEach { charResult ->
                        charResult.drops.filter { drop -> drop.code == itemCode }.forEach { drop ->
                            done += drop.quantity
                        }
                    }
                    log.info("Done: $done / $quantity")
                } while (done <= quantity)
            }
        }catch (e: Exception){
            log.error("Error running boss fights", e)
        }

            threadService.startCharacterThread(CharacterConfig.getPredefinedCharactersMap().get(character2Name)!!)
            threadService.startCharacterThread(CharacterConfig.getPredefinedCharactersMap().get(character3Name)!!)

        return accountClient.getCharacter(character1Name).data

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
