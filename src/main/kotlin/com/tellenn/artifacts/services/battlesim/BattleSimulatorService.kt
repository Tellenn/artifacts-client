package com.tellenn.artifacts.services.battlesim

import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Effect
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.db.repositories.MonsterRepository
import org.springframework.stereotype.Service
import kotlin.math.roundToInt


// Class to represent item information for battle simulation
data class ItemInformation(
    val code: String,
    val name: String,
    val effects: List<Effect>
)

// Class to represent battle analysis result
data class BattleAnalysis(
    val characterHpRemaining: Int,
    val monsterDamagePerTurn: Int,
    val characterDamagePerTurn: Int,
    val turnsToTriggerPotions: Int,
    val turnsToKill: Int,
    val utility1: ItemInformation?,
    val utility2: ItemInformation?
)

@Service
class BattleSimulatorService(
    private val monsterRepository: MonsterRepository,
    private val itemRepository: ItemRepository
) {
    // Class to hold damage calculation result
    data class DamageResult(
        val damage: Int,
        val lifestealAmount: Int = 0
    )

    // Utility class to get item information
    private val itemUtils = object {
        fun getItemInfo(itemCode: String?): ItemInformation? {
            if (itemCode == null || itemCode.isEmpty()) return null

            val item = itemRepository.findByCode(itemCode) ?: return null

            return ItemInformation(
                code = item.code,
                name = item.name,
                effects = item.effects?.map {
                    Effect(
                        code = it.code,
                        value = it.value,
                        description = it.description
                    )
                } ?: emptyList()
            )
        }
    }

    fun simulate(monsterCode: String, character: ArtifactsCharacter): BattleSimulatorResult {
        val monster = monsterRepository.findByCode(monsterCode)
            ?: throw IllegalArgumentException("Monster with code $monsterCode not found")

        // Get utility items
        val utility1: ItemInformation? = if (character.utility1Slot != null && character.utility1Slot != "") {
            itemUtils.getItemInfo(character.utility1Slot)
        } else null

        val utility2: ItemInformation? = if (character.utility2Slot != null && character.utility2Slot != "") {
            itemUtils.getItemInfo(character.utility2Slot)
        } else null

        // Calculate character HP with boost_hp effects
        var characterHp: Int = character.hp
        if (utility1 != null) {
            for (effect in utility1.effects) {
                if (effect.code == "boost_hp") {
                    characterHp += effect.value
                }
            }
        }
        if (utility2 != null) {
            for (effect in utility2.effects) {
                if (effect.code == "boost_hp") {
                    characterHp += effect.value
                }
            }
        }

        // Calculate character damage
        var computedFireDamage = (character.attackFire.toDouble() * (character.dmgFire.toDouble() / 100 + 1) * (1 - monster.defenseFire.toDouble() / 100)).roundToInt()
        var computedEarthDamage = (character.attackEarth.toDouble() * (character.dmgEarth.toDouble() / 100 + 1) * (1 - monster.defenseEarth.toDouble() / 100)).roundToInt()
        var computedWaterDamage = (character.attackWater.toDouble() * (character.dmgWater.toDouble() / 100 + 1) * (1 - monster.defenseWater.toDouble() / 100)).roundToInt()
        var computedAirDamage = (character.attackAir.toDouble() * (character.dmgAir.toDouble() / 100 + 1) * (1 - monster.defenseAir.toDouble() / 100)).roundToInt()

        // Apply damage boost effects from utility1
        if (utility1 != null) {
            for (effect in utility1.effects) {
                when (effect.code) {
                    "boost_dmg_fire" -> computedFireDamage = (computedFireDamage.toDouble() * (effect.value.toDouble() / 100 + 1)).roundToInt()
                    "boost_dmg_earth" -> computedEarthDamage = (computedEarthDamage.toDouble() * (effect.value.toDouble() / 100 + 1)).roundToInt()
                    "boost_dmg_water" -> computedWaterDamage = (computedWaterDamage.toDouble() * (effect.value.toDouble() / 100 + 1)).roundToInt()
                    "boost_dmg_air" -> computedAirDamage = (computedAirDamage.toDouble() * (effect.value.toDouble() / 100 + 1)).roundToInt()
                }
            }
        }

        // Apply damage boost effects from utility2
        if (utility2 != null) {
            for (effect in utility2.effects) {
                when (effect.code) {
                    "boost_dmg_fire" -> computedFireDamage = (computedFireDamage.toDouble() * (effect.value.toDouble() / 100 + 1)).roundToInt()
                    "boost_dmg_earth" -> computedEarthDamage = (computedEarthDamage.toDouble() * (effect.value.toDouble() / 100 + 1)).roundToInt()
                    "boost_dmg_water" -> computedWaterDamage = (computedWaterDamage.toDouble() * (effect.value.toDouble() / 100 + 1)).roundToInt()
                    "boost_dmg_air" -> computedAirDamage = (computedAirDamage.toDouble() * (effect.value.toDouble() / 100 + 1)).roundToInt()
                }
            }
        }

        // Calculate base damage per turn
        var baseDamagePerTurn = computedFireDamage + computedEarthDamage + computedWaterDamage + computedAirDamage

        // Apply critical strike chance
        // Calculate average damage with critical strikes factored in
        val criticalStrikeChance = character.criticalStrike.toDouble() / 100.0
        val criticalDamageMultiplier = 1.5 // Critical strikes add 50% extra damage
        val averageDamageMultiplier = 1.0 + (criticalStrikeChance * (criticalDamageMultiplier - 1.0))
        var characterDamagePerTurn = (baseDamagePerTurn * averageDamageMultiplier).roundToInt()

        // Calculate lifesteal from critical strikes
        var lifestealPerTurn = 0
        if (character.criticalStrike > 0) {
            // Check for lifesteal effect in utility items
            var lifestealPercent = 0
            if (utility1 != null) {
                for (effect in utility1.effects) {
                    if (effect.code == "lifesteal") {
                        lifestealPercent = effect.value
                        break
                    }
                }
            }
            if (lifestealPercent == 0 && utility2 != null) {
                for (effect in utility2.effects) {
                    if (effect.code == "lifesteal") {
                        lifestealPercent = effect.value
                        break
                    }
                }
            }

            if (lifestealPercent > 0) {
                // Calculate total attack
                val totalAttack = character.attackFire + character.attackWater + character.attackEarth + character.attackAir
                // Calculate lifesteal amount per critical strike
                val lifestealAmount = totalAttack * lifestealPercent / 100
                // Calculate average lifesteal per turn based on critical strike chance
                lifestealPerTurn = (lifestealAmount * criticalStrikeChance).roundToInt()
            }
        }

        // Check for burn effect on monster
        for (effect in monster.effects) {
            if (effect.code == "burn") {
                // Calculate initial burn damage (value% of character's total attack)
                val totalCharacterAttack = character.attackFire + character.attackWater + character.attackEarth + character.attackAir
                val initialBurnDamage = (totalCharacterAttack * effect.value / 100)

                // Calculate average burn damage over multiple turns, accounting for 10% decrease each turn
                var totalBurnDamage = 0
                var currentBurnDamage = initialBurnDamage
                val maxTurns = 10 // Reasonable number of turns to consider

                for (turn in 1..maxTurns) {
                    totalBurnDamage += currentBurnDamage
                    currentBurnDamage = (currentBurnDamage * 0.9).toInt() // Decrease by 10% each turn
                    if (currentBurnDamage <= 0) break
                }

                // Calculate average burn damage per turn
                val averageBurnDamage = totalBurnDamage / maxTurns

                // Add average burn damage to character's damage per turn
                characterDamagePerTurn += averageBurnDamage
                break // Only consider one burn effect
            }
        }

        // Calculate monster damage
        var monsterFireDamage = (monster.attackFire.toDouble() * (1 - character.resFire.toDouble() / 100)).roundToInt()
        var monsterEarthDamage = (monster.attackEarth.toDouble() * (1 - character.resEarth.toDouble() / 100)).roundToInt()
        var monsterWaterDamage = (monster.attackWater.toDouble() * (1 - character.resWater.toDouble() / 100)).roundToInt()
        var monsterAirDamage = (monster.attackAir.toDouble() * (1 - character.resAir.toDouble() / 100)).roundToInt()

        // Apply resistance boost effects from utility1
        if (utility1 != null) {
            for (effect in utility1.effects) {
                when (effect.code) {
                    "boost_res_fire" -> monsterFireDamage = (monsterFireDamage.toDouble() * (1 - effect.value.toDouble() / 100)).roundToInt()
                    "boost_res_earth" -> monsterEarthDamage = (monsterEarthDamage.toDouble() * (1 - effect.value.toDouble() / 100)).roundToInt()
                    "boost_res_water" -> monsterWaterDamage = (monsterWaterDamage.toDouble() * (1 - effect.value.toDouble() / 100)).roundToInt()
                    "boost_res_air" -> monsterAirDamage = (monsterAirDamage.toDouble() * (1 - effect.value.toDouble() / 100)).roundToInt()
                }
            }
        }

        // Apply resistance boost effects from utility2
        if (utility2 != null) {
            for (effect in utility2.effects) {
                when (effect.code) {
                    "boost_res_fire" -> monsterFireDamage = (monsterFireDamage.toDouble() * (1 - effect.value.toDouble() / 100)).roundToInt()
                    "boost_res_earth" -> monsterEarthDamage = (monsterEarthDamage.toDouble() * (1 - effect.value.toDouble() / 100)).roundToInt()
                    "boost_res_water" -> monsterWaterDamage = (monsterWaterDamage.toDouble() * (1 - effect.value.toDouble() / 100)).roundToInt()
                    "boost_res_air" -> monsterAirDamage = (monsterAirDamage.toDouble() * (1 - effect.value.toDouble() / 100)).roundToInt()
                }
            }
        }

        val monsterDamagePerTurn = monsterFireDamage + monsterEarthDamage + monsterWaterDamage + monsterAirDamage

        // Calculate turns to kill and turns to trigger potions
        val turnsToKill: Int = if (characterDamagePerTurn > 0) {
            val calculatedTurns = (monster.hp.toDouble() / characterDamagePerTurn.toDouble()).roundToInt()
            maxOf(1, calculatedTurns) // Ensure at least 1 turn
        } else Int.MAX_VALUE
        val turnsToTriggerPotions = if (monsterDamagePerTurn > 0) turnsToKill - (characterHp / monsterDamagePerTurn) / 2 else 0


        // Apply restore effects
        if (utility1 != null && turnsToTriggerPotions > 0) {
            for (effect in utility1.effects) {
                if (effect.code == "restore") {
                    characterHp += effect.value * turnsToTriggerPotions
                }
            }
        }
        if (utility2 != null && turnsToTriggerPotions > 0) {
            for (effect in utility2.effects) {
                if (effect.code == "restore") {
                    characterHp += effect.value * turnsToTriggerPotions
                }
            }
        }

        // Apply lifesteal healing over the course of the battle
        val totalLifestealHealing = lifestealPerTurn * turnsToKill

        // Create battle analysis
        val battleAnalysis = BattleAnalysis(
            characterHpRemaining = characterHp - turnsToKill * monsterDamagePerTurn + totalLifestealHealing,
            monsterDamagePerTurn = monsterDamagePerTurn,
            characterDamagePerTurn = characterDamagePerTurn,
            turnsToTriggerPotions = turnsToTriggerPotions,
            turnsToKill = turnsToKill,
            utility1 = utility1,
            utility2 = utility2
        )

        // Determine if character wins
        val win = battleAnalysis.characterHpRemaining > 0

        // Calculate monster HP remaining
        val monsterHpRemaining = if (win) 0 else {
            // If character loses, calculate how many turns the character survives
            val turnsCharacterSurvives = if (monsterDamagePerTurn > 0) (characterHp / monsterDamagePerTurn) else Int.MAX_VALUE
            // Calculate total turns in the battle (character + monster turns)
            val totalTurns = turnsCharacterSurvives * 2
            // Character attacks on odd turns (1, 3, 5, ...), so they get (totalTurns + 1) / 2 attacks
            val characterAttacks = (totalTurns + 1) / 2
            // Calculate monster HP remaining
            maxOf(0, monster.hp - characterAttacks * characterDamagePerTurn)
        }

        // Return BattleSimulatorResult
        return BattleSimulatorResult(
            win = win,
            monsterHpRemaining = monsterHpRemaining,
            characterHpRemaining = if (win) battleAnalysis.characterHpRemaining else 0,
            turns = turnsToKill
        )
    }
}

class BattleSimulatorResult(
    val win: Boolean,
    val monsterHpRemaining: Int,
    val characterHpRemaining: Int,
    val turns: Int
)
