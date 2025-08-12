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
        // TODO : Doesn't work, exemple of fight :

        /**
         * Fight start: Character HP: 455/455, Monster HP: 650/650
         * Turn 1: Character used fire attack and dealt 60 damage. (Monster HP: 590/650)
         * Turn 2: Monster used earth attack and dealt 70 damage. (Character HP: 385/455)
         * Turn 3: Character used fire attack and dealt 60 damage. (Monster HP: 530/650)
         * Turn 4: Monster used earth attack and dealt 70 damage. (Character HP: 315/455)
         * Turn 5: Character used fire attack and dealt 60 damage. (Monster HP: 470/650)
         * Turn 6: Monster used earth attack and dealt 70 damage. (Character HP: 245/455)
         * Turn 7: Character used fire attack and dealt 60 damage. (Monster HP: 410/650)
         * Turn 8: Monster used earth attack and dealt 105 damage (Critical strike). (Character HP: 140/455)
         * Turn 9: Character used Minor Health Potion and restored 45 HP. (HP: 185/455)
         * Turn 9: Character used fire attack and dealt 60 damage. (Monster HP: 350/650)
         * Turn 10: Monster used earth attack and dealt 70 damage. (Character HP: 115/455)
         * Turn 11: Character used Minor Health Potion and restored 45 HP. (HP: 160/455)
         * Turn 11: Character used fire attack and dealt 60 damage. (Monster HP: 290/650)
         * Turn 12: Monster used earth attack and dealt 70 damage. (Character HP: 90/455)
         * Turn 13: Character used Minor Health Potion and restored 45 HP. (HP: 135/455)
         * Turn 13: Character used fire attack and dealt 90 damage (Critical strike). (Monster HP: 200/650)
         * Turn 14: Monster used earth attack and dealt 70 damage. (Character HP: 65/455)
         * Turn 15: Character used Minor Health Potion and restored 45 HP. (HP: 110/455)
         * Turn 15: Character used fire attack and dealt 60 damage. (Monster HP: 140/650)
         * Turn 16: Monster used earth attack and dealt 70 damage. (Character HP: 40/455)
         * Turn 17: Character used Minor Health Potion and restored 45 HP. (HP: 85/455)
         * Turn 17: Character used fire attack and dealt 60 damage. (Monster HP: 80/650)
         * Turn 18: Monster used earth attack and dealt 70 damage. (Character HP: 15/455)
         * Turn 19: Character used Minor Health Potion and restored 45 HP. (HP: 60/455)
         * Turn 19: Character used fire attack and dealt 60 damage. (Monster HP: 20/650)
         * Turn 20: Monster used earth attack and dealt 70 damage. (Character HP: 0/455)
         * Fight result: loss. Character HP: 0/455, Monster HP: 20/650
         *
         * Renoir
         *
         *     {
         *       "name": "Renoir",
         *       "account": "Tellenn",
         *       "skin": "men1",
         *       "level": 26,
         *       "xp": 33,
         *       "max_xp": 14600,
         *       "gold": 0,
         *       "speed": 100,
         *       "mining_level": 7,
         *       "mining_xp": 819,
         *       "mining_max_xp": 1200,
         *       "woodcutting_level": 1,
         *       "woodcutting_xp": 0,
         *       "woodcutting_max_xp": 150,
         *       "fishing_level": 1,
         *       "fishing_xp": 0,
         *       "fishing_max_xp": 150,
         *       "weaponcrafting_level": 20,
         *       "weaponcrafting_xp": 3295,
         *       "weaponcrafting_max_xp": 8200,
         *       "gearcrafting_level": 15,
         *       "gearcrafting_xp": 4050,
         *       "gearcrafting_max_xp": 4400,
         *       "jewelrycrafting_level": 16,
         *       "jewelrycrafting_xp": 114,
         *       "jewelrycrafting_max_xp": 5100,
         *       "cooking_level": 1,
         *       "cooking_xp": 0,
         *       "cooking_max_xp": 150,
         *       "alchemy_level": 16,
         *       "alchemy_xp": 104,
         *       "alchemy_max_xp": 5100,
         *       "hp": 455,
         *       "max_hp": 455,
         *       "haste": 4,
         *       "critical_strike": 5,
         *       "wisdom": 0,
         *       "prospecting": 0,
         *       "attack_fire": 40,
         *       "attack_earth": 0,
         *       "attack_water": 0,
         *       "attack_air": 0,
         *       "dmg": 0,
         *       "dmg_fire": 26,
         *       "dmg_earth": 10,
         *       "dmg_water": 8,
         *       "dmg_air": 8,
         *       "res_fire": 13,
         *       "res_earth": 13,
         *       "res_water": 2,
         *       "res_air": 2,
         *       "x": 4,
         *       "y": 1,
         *       "cooldown": 21,
         *       "cooldown_expiration": "2025-08-12T14:15:37.540Z",
         *       "weapon_slot": "skull_staff",
         *       "rune_slot": "",
         *       "shield_slot": "wooden_shield",
         *       "helmet_slot": "iron_helm",
         *       "body_armor_slot": "leather_armor",
         *       "leg_armor_slot": "iron_legs_armor",
         *       "boots_slot": "leather_boots",
         *       "ring1_slot": "fire_ring",
         *       "ring2_slot": "fire_ring",
         *       "amulet_slot": "life_amulet",
         *       "artifact1_slot": "",
         *       "artifact2_slot": "",
         *       "artifact3_slot": "",
         *       "utility1_slot": "minor_health_potion",
         *       "utility1_slot_quantity": 87,
         *       "utility2_slot": "",
         *       "utility2_slot_quantity": 0,
         *       "bag_slot": "",
         *       "task": "",
         *       "task_type": "",
         *       "task_progress": 0,
         *       "task_total": 0,
         *       "inventory_max_items": 150,
         *       "inventory": [
         *         {
         *           "slot": 1,
         *           "code": "adventurer_pants",
         *           "quantity": 1
         *         },
         *         {
         *           "slot": 2,
         *           "code": "air_and_water_amulet",
         *           "quantity": 1
         *         },
         *         {
         *           "slot": 3,
         *           "code": "battlestaff",
         *           "quantity": 1
         *         },
         *         {
         *           "slot": 4,
         *           "code": "water_ring",
         *           "quantity": 2
         *         },
         *         {
         *           "slot": 5,
         *           "code": "lucky_wizard_hat",
         *           "quantity": 1
         *         },
         *         {
         *           "slot": 6,
         *           "code": "adventurer_boots",
         *           "quantity": 1
         *         },
         *         {
         *           "slot": 7,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 8,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 9,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 10,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 11,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 12,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 13,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 14,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 15,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 16,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 17,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 18,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 19,
         *           "code": "",
         *           "quantity": 0
         *         },
         *         {
         *           "slot": 20,
         *           "code": "",
         *           "quantity": 0
         *         }
         *       ]
         *     }
         *
         *
         */
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
