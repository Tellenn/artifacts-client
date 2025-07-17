package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.Effect
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.documents.MonsterDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.db.repositories.MonsterRepository
import org.springframework.data.domain.PageRequest
import kotlin.math.roundToInt
import kotlin.random.Random

/*
The battle is made turn by turn.
Assuming the character starts, it will do damage based on his various attack values, minus the percentage of defense of the monster
Then the opposite exist.

In order to do an optimised battle simulator, create the mean damage output and check in how many turn either one of the character/monster dies (0 or less hp)

Then, take into account any potential item such as :

Food for hp boost at the beginning, potion that can increase damage or restore health. They are indicated in the character in the consumable slots.
You also need to take in account the rune equipped.

Here is a list of effect to take into account for now : 

{
  "data": [
    {
      "name": "Boost HP",
      "code": "boost_hp",
      "description": "Add {value}HP at the start of the fight and for the rest of the fight.",
      "type": "combat",
      "subtype": "buff"
    },
    {
      "name": "Boost Damage Fire",
      "code": "boost_dmg_fire",
      "description": "Add {value}% fire damage at the start of the fight and for the rest of the fight.",
      "type": "combat",
      "subtype": "buff"
    },
    {
      "name": "Boost Damage Water",
      "code": "boost_dmg_water",
      "description": "Add {value}% water damage at the start of the fight and for the rest of the fight.",
      "type": "combat",
      "subtype": "buff"
    },
    {
      "name": "Boost Damage Air",
      "code": "boost_dmg_air",
      "description": "Add {value}% air damage at the start of the fight and for the rest of the fight.",
      "type": "combat",
      "subtype": "buff"
    },
    {
      "name": "Boost Damage Earth",
      "code": "boost_dmg_earth",
      "description": "Add {value}% earth damage at the start of the fight and for the rest of the fight.",
      "type": "combat",
      "subtype": "buff"
    },
    {
      "name": "Restore",
      "code": "restore",
      "description": "Restores {value}HP at the start of the turn if the player has lost more than 50% of their health points.",
      "type": "combat",
      "subtype": "heal"
    },
    {
      "name": "Healing",
      "code": "healing",
      "description": "Every 3 played turns, restores {value}% of HP at the start of the turn.\n",
      "type": "combat",
      "subtype": "special"
    },
    {
      "name": "Antipoison",
      "code": "antipoison",
      "description": "At the beginning of the turn, if the character has at least one poison on him, removes {value} poison damage.",
      "type": "combat",
      "subtype": "other"
    },
    {
      "name": "Poison",
      "code": "poison",
      "description": "At the start of its first turn, applies a {value} poison to its opponent. Loses {value} HP per turn.",
      "type": "combat",
      "subtype": "special"
    },
    {
      "name": "Lifesteal",
      "code": "lifesteal",
      "description": "Restores {value}% of the total attack of all elements in HP after a critical strike.",
      "type": "combat",
      "subtype": "special"
    },
    {
      "name": "Reconstitution",
      "code": "reconstitution",
      "description": "At the beginning of the turn {value}, regains all HP.",
      "type": "combat",
      "subtype": "special"
    },
    {
      "name": "Burn",
      "code": "burn",
      "description": "On his first turn, apply a burn effect of {value}% of your attack of all elements. The damage is applied each turn and decreases by 10% each time.",
      "type": "combat",
      "subtype": "special"
    },
    {
      "name": "Boost Resistance Air",
      "code": "boost_res_air",
      "description": "Gives {value}% air resistance at the start of fight.",
      "type": "combat",
      "subtype": "buff"
    },
    {
      "name": "Boost Resistance Water",
      "code": "boost_res_water",
      "description": "Gives {value}% water resistance at the start of fight.",
      "type": "combat",
      "subtype": "buff"
    },
    {
      "name": "Boost Resistance Earth",
      "code": "boost_res_earth",
      "description": "Gives {value}% earth resistance at the start of fight.",
      "type": "combat",
      "subtype": "buff"
    },
    {
      "name": "Boost Resistance Fire",
      "code": "boost_res_fire",
      "description": "Gives {value}% fire resistance at the start of fight.",
      "type": "combat",
      "subtype": "buff"
    },
    {
      "name": "Corrupted",
      "code": "corrupted",
      "description": "After every time the bearer of this effect is attacked, their resistance to the attack's element is reduced by {value}%, and can go negative.",
      "type": "combat",
      "subtype": "special"
    },
    {
      "name": "Heal",
      "code": "heal",
      "description": "Heal {value} HP when the item is used.",
      "type": "consumable",
      "subtype": "heal"
    },
    {
      "name": "Teleport X",
      "code": "teleport_x",
      "description": "Teleports to x-coordinate {value} when the item is used.",
      "type": "consumable",
      "subtype": "teleport"
    },
    {
      "name": "Gold",
      "code": "gold",
      "description": "Adds {value} gold in your inventory.",
      "type": "consumable",
      "subtype": "gold"
    },
    {
      "name": "Teleport Y",
      "code": "teleport_y",
      "description": "Teleports to y-coordinate {value} when the item is used.",
      "type": "consumable",
      "subtype": "teleport"
    },
    {
      "name": "Fire Attack",
      "code": "attack_fire",
      "description": "Adds {value} Fire Attack to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Water Attack",
      "code": "attack_water",
      "description": "Adds {value} Water Attack to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Air Attack",
      "code": "attack_air",
      "description": "Adds {value} Air Attack to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Earth Attack",
      "code": "attack_earth",
      "description": "Adds {value} Earth Attack to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Damage",
      "code": "dmg",
      "description": "Adds {value}% Damage to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Fire Damage",
      "code": "dmg_fire",
      "description": "Adds {value}% Fire Damage to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Water Damage",
      "code": "dmg_water",
      "description": "Adds {value}% Water Damage to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Air Damage",
      "code": "dmg_air",
      "description": "Adds {value}% Air Damage to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Earth Damage",
      "code": "dmg_earth",
      "description": "Adds {value}% Earth Damage to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Fire Resistance",
      "code": "res_fire",
      "description": "Adds {value}% Fire Res to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Water Resistance",
      "code": "res_water",
      "description": "Adds {value}% Water Res to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Air Resistance",
      "code": "res_air",
      "description": "Adds {value}% Air Res to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Earth Resistance",
      "code": "res_earth",
      "description": "Adds {value}% Earth Res to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Critical Strike",
      "code": "critical_strike",
      "description": "Adds {value}% Critical Strike to its stats when equipped. Critical strikes adds 50% extra damage to an attack (1.5x). ",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Wisdom",
      "code": "wisdom",
      "description": "Adds {value} Wisdom to its stats when equipped. Each point of wisdom increases your xp in combat and with skills. (1% extra per 10 wisdom)",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Prospecting",
      "code": "prospecting",
      "description": "Adds {value} Prospecting to its stats when equipped. Each PP increases your chance of obtaining drops in combat and with skills. (1% extra per 10 PP)",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Woodcutting",
      "code": "woodcutting",
      "description": "Reduces cooldown by {value}% when a character logs a tree.",
      "type": "equipment",
      "subtype": "gathering"
    },
    {
      "name": "Fishing",
      "code": "fishing",
      "description": "Reduces cooldown by {value}% when a character is fishing.",
      "type": "equipment",
      "subtype": "gathering"
    },
    {
      "name": "Mining",
      "code": "mining",
      "description": "Reduces cooldown by {value}% when a character mines a resource.",
      "type": "equipment",
      "subtype": "gathering"
    },
    {
      "name": "Alchemy",
      "code": "alchemy",
      "description": "Reduces cooldown by {value}% when a character harvest a plant.",
      "type": "equipment",
      "subtype": "gathering"
    },
    {
      "name": "Hit points",
      "code": "hp",
      "description": "Adds {value} HP to its stats when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Inventory Space",
      "code": "inventory_space",
      "description": "Adds {value} to the maximum number of items in the inventory when equipped.",
      "type": "equipment",
      "subtype": "stat"
    },
    {
      "name": "Haste",
      "code": "haste",
      "description": "Adds {value} Haste to its stats when equipped. The haste reduces the cooldown of a fight. ",
      "type": "equipment",
      "subtype": "stat"
    }
  ],
  "total": 44,
  "page": 1,
  "size": 50,
  "pages": 1
}


The result should be BattleSimulatorResult and all information related to it.

Make tests out of it as well
 */

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
        val utility1: ItemInformation? = if (character.utility1Slot != null && !character.utility1Slot.isEmpty()) {
            itemUtils.getItemInfo(character.utility1Slot)
        } else null

        val utility2: ItemInformation? = if (character.utility2Slot != null && !character.utility2Slot.isEmpty()) {
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
