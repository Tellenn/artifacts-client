package com.tellenn.artifacts.services

import com.tellenn.artifacts.MainRuntime
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BattleClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.exceptions.BattleLostException
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
     * Official entry point for boss fights triggered during gathering.
     * Checks that all default characters are available, simulates the fight,
     * then executes if both conditions are met.
     *
     * @throws BattleLostException if characters are unavailable or the simulation predicts defeat
     */
    fun tryFightForItem(monsterCode: String, itemCode: String, quantity: Int): ArtifactsCharacter {
        val allAvailable = listOf(DEFAULT_CHARACTER_1, DEFAULT_CHARACTER_2, DEFAULT_CHARACTER_3)
            .none { threadService.isCharacterOnMission(it) }

        if (!allAvailable) {
            log.info("Boss fight for $monsterCode skipped: not all default characters are available")
            throw BattleLostException(monsterCode)
        }

        if (!simulateBossFight(monsterCode)) {
            log.info("Boss fight simulation failed for $monsterCode: party is not strong enough yet")
            throw BattleLostException(monsterCode)
        }

        return runBossFights(monsterCode = monsterCode, itemCode = itemCode, quantity = quantity)
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
        val monster = monsterService.findMonster(monsterCode)
        if (monster.type != "boss") {
            throw IllegalArgumentException("Monster $monsterCode is not a boss (type: ${monster.type})")
        }
        return simulateParty(character1Name, character2Name, character3Name, monsterCode)
    }

    /**
     * Atomically reserves the three-character party: reserve the master, then
     * interrupt-reserve the two slaves. Rolls back on partial failure.
     * @return true iff all three were reserved.
     */
    fun reserveParty(character1Name: String, character2Name: String, character3Name: String): Boolean {
        if (!threadService.reserveCharacter(character1Name)) {
            log.info("Party reservation failed: $character1Name is already on a mission")
            return false
        }
        if (!threadService.reserveAndInterruptCharacter(character2Name)) {
            log.info("Party reservation failed: $character2Name is already on a mission")
            threadService.releaseCharacter(character1Name)
            return false
        }
        if (!threadService.reserveAndInterruptCharacter(character3Name)) {
            log.info("Party reservation failed: $character3Name is already on a mission")
            threadService.releaseCharacter(character1Name)
            threadService.restartCharacterThread(character2Name)
            return false
        }
        return true
    }

    /** Releases the master and restarts the two slave threads. */
    fun releaseParty(character1Name: String, character2Name: String, character3Name: String) {
        threadService.releaseCharacter(character1Name)
        threadService.restartCharacterThread(character2Name)
        threadService.restartCharacterThread(character3Name)
    }

    /**
     * Moves the party to the bank, equips best gear for [monsterCode]
     * (char1 tank with high threat, char2 pure DPS, char3 DPS forced to a healing rune),
     * then moves all three to the monster's map cell. Returns the positioned characters.
     */
    fun prepareParty(
        character1Name: String,
        character2Name: String,
        character3Name: String,
        monsterCode: String,
    ): Triple<ArtifactsCharacter, ArtifactsCharacter, ArtifactsCharacter> {
        var char1 = accountClient.getCharacter(character1Name).data
        var char2 = accountClient.getCharacter(character2Name).data
        var char3 = accountClient.getCharacter(character3Name).data

        char1 = movementService.moveToBank(char1)
        char2 = movementService.moveToBank(char2)
        char3 = movementService.moveToBank(char3)

        // Treat char1 as a Tank: maximize threat
        char1 = equipmentService.equipBestAvailableEquipmentForMonsterInBank(
            character = char1,
            monsterCode = monsterCode,
            threatScoreMult = 10,
        )
        char2 = equipmentService.equipBestAvailableEquipmentForMonsterInBank(char2, monsterCode)
        char3 = equipmentService.equipBestAvailableEquipmentForMonsterInBank(char3, monsterCode)
        char3 = ensureHealingRune(char3)

        val map = mapService.findClosestMap(char1, contentCode = monsterCode)
        char1 = movementService.moveCharacterToCell(map.mapId, char1)
        char2 = movementService.moveCharacterToCell(map.mapId, char2)
        char3 = movementService.moveCharacterToCell(map.mapId, char3)

        return Triple(char1, char2, char3)
    }

    /** Forces a healing_aura_rune on the given character (from bank, or bought if affordable). */
    private fun ensureHealingRune(character: ArtifactsCharacter): ArtifactsCharacter {
        var char3 = character
        if (char3.runeSlot != "healing_aura_rune") {
            if (bankService.isInBank("healing_aura_rune", 1)) {
                val oldRune = char3.runeSlot
                char3 = bankService.withdrawOne("healing_aura_rune", 1, char3)
                char3 = characterService.equip(char3, "healing_aura_rune", "rune", 1)
                if (oldRune != null) {
                    char3 = bankService.deposit(char3, listOf(SimpleItem(oldRune, 1)))
                }
            } else if (bankService.getBankDetails().gold > 25000) {
                char3 = merchantService.buy("healing_aura_rune", char3)
                val oldRune = char3.runeSlot
                char3 = characterService.equip(char3, "healing_aura_rune", "rune", 1)
                if (oldRune != null) {
                    char3 = movementService.moveToBank(char3)
                    char3 = bankService.deposit(char3, listOf(SimpleItem(oldRune, 1)))
                }
            }
        }
        return char3
    }

    /**
     * Gears the three characters and simulates the fight against [monsterCode].
     * Unlike [simulateBossFight], this does NOT require the monster to be a boss type,
     * so it can be used for raid monsters.
     */
    fun simulateParty(
        character1Name: String,
        character2Name: String,
        character3Name: String,
        monsterCode: String,
    ): Boolean {
        val monster = monsterService.findMonster(monsterCode)
        val character1 = getBestGearForCharacter(accountClient.getCharacter(character1Name).data, monsterCode)
        val character2 = getBestGearForCharacter(accountClient.getCharacter(character2Name).data, monsterCode)
        val character3 = getBestGearForCharacter(accountClient.getCharacter(character3Name).data, monsterCode)
        return runFightSimulation(listOf(character1, character2, character3), monster)
    }



    /**
     * Runs a boss fight with three characters.
     * Atomically reserves all three characters before starting so WebSocket events
     * cannot interfere with char1's thread or assign missions to char2/char3 mid-fight.
     *
     * @param monsterCode The code of the monster to fight
     * @param character1Name Master character (tank) — its thread drives the fight
     * @param character2Name Slave DPS character — thread stopped for the duration
     * @param character3Name Slave healer/DPS character — thread stopped for the duration
     */
    fun runBossFights(
        monsterCode: String,
        itemCode: String,
        quantity: Int = 20,
        character1Name: String = DEFAULT_CHARACTER_1,
        character2Name: String = DEFAULT_CHARACTER_2,
        character3Name: String = DEFAULT_CHARACTER_3,
    ): ArtifactsCharacter {
        if (!reserveParty(character1Name, character2Name, character3Name)) {
            log.info("Boss fight for $monsterCode skipped: party not available")
            throw BattleLostException(monsterCode)
        }

        try {
            var (char1, char2, char3) = prepareParty(character1Name, character2Name, character3Name, monsterCode)
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
        } catch (e: Exception) {
            log.error("Error running boss fights", e)
        } finally {
            releaseParty(character1Name, character2Name, character3Name)
        }

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
