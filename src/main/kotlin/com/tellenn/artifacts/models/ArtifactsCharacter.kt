package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.AppConfig
import java.time.Instant
import kotlin.math.min

@Suppress("unused")
class ArtifactsCharacter(
    var name: String,
    var account: String,
    var level: Int,
    var gold: Int,
    var hp: Int,
    @JsonProperty("max_hp") var maxHp: Int,
    var x: Int,
    var y: Int,
    var inventory: Array<InventorySlot>,
    var cooldown: Int,
    var skin: String?,
    var task: String?,
    @JsonProperty("dmg") var dmg: Int,
    @JsonProperty("wisdom") var wisdom: Int,
    @JsonProperty("prospecting") var prospecting: Int,
    @JsonProperty("critical_strike") var criticalStrike: Int,
    @JsonProperty("speed") var speed: Int,
    @JsonProperty("haste") var haste: Int,
    @JsonProperty("xp") var xp: Int,
    @JsonProperty("max_xp") var maxXp: Int,
    @JsonProperty("task_type") var taskType: String?,
    @JsonProperty("task_total") var taskTotal: Int,
    @JsonProperty("task_progress") var taskProgress: Int,
    @JsonProperty("mining_xp") var miningXp: Int,
    @JsonProperty("mining_max_xp") var miningMaxXp: Int,
    @JsonProperty("mining_level") var miningLevel: Int,
    @JsonProperty("woodcutting_xp") var woodcuttingXp: Int,
    @JsonProperty("woodcutting_max_xp") var woodcuttingMaxXp: Int,
    @JsonProperty("woodcutting_level") var woodcuttingLevel: Int,
    @JsonProperty("fishing_xp") var fishingXp: Int,
    @JsonProperty("fishing_max_xp") var fishingMaxXp: Int,
    @JsonProperty("fishing_level") var fishingLevel: Int,
    @JsonProperty("weaponcrafting_xp") var weaponcraftingXp: Int,
    @JsonProperty("weaponcrafting_max_xp") var weaponcraftingMaxXp: Int,
    @JsonProperty("weaponcrafting_level") var weaponcraftingLevel: Int,
    @JsonProperty("gearcrafting_xp") var gearcraftingXp: Int,
    @JsonProperty("gearcrafting_max_xp") var gearcraftingMaxXp: Int,
    @JsonProperty("gearcrafting_level") var gearcraftingLevel: Int,
    @JsonProperty("jewelrycrafting_xp") var jewelrycraftingXp: Int,
    @JsonProperty("jewelrycrafting_max_xp") var jewelrycraftingMaxXp: Int,
    @JsonProperty("jewelrycrafting_level") var jewelrycraftingLevel: Int,
    @JsonProperty("cooking_xp") var cookingXp: Int,
    @JsonProperty("cooking_max_xp") var cookingMaxXp: Int,
    @JsonProperty("cooking_level") var cookingLevel: Int,
    @JsonProperty("alchemy_xp") var alchemyXp: Int,
    @JsonProperty("alchemy_max_xp") var alchemyMaxXp: Int,
    @JsonProperty("alchemy_level") var alchemyLevel: Int,
    @JsonProperty("inventory_max_items") var inventoryMaxItems: Int,
    @JsonProperty("attack_fire") var attackFire: Int,
    @JsonProperty("attack_earth") var attackEarth: Int,
    @JsonProperty("attack_water") var attackWater: Int,
    @JsonProperty("attack_air") var attackAir: Int,
    @JsonProperty("dmg_fire") var dmgFire: Int,
    @JsonProperty("dmg_earth") var dmgEarth: Int,
    @JsonProperty("dmg_water") var dmgWater: Int,
    @JsonProperty("dmg_air") var dmgAir: Int,
    @JsonProperty("res_fire") var resFire: Int,
    @JsonProperty("res_earth") var resEarth: Int,
    @JsonProperty("res_water") var resWater: Int,
    @JsonProperty("res_air") var resAir: Int,
    @JsonProperty("weapon_slot") var weaponSlot: String?,
    @JsonProperty("rune_slot") var runeSlot: String?,
    @JsonProperty("shield_slot") var shieldSlot: String?,
    @JsonProperty("helmet_slot") var helmetSlot: String?,
    @JsonProperty("body_armor_slot") var bodyArmorSlot: String?,
    @JsonProperty("leg_armor_slot") var legArmorSlot: String?,
    @JsonProperty("boots_slot") var bootsSlot: String?,
    @JsonProperty("ring1_slot") var ring1Slot: String?,
    @JsonProperty("ring2_slot") var ring2Slot: String?,
    @JsonProperty("amulet_slot") var amuletSlot: String?,
    @JsonProperty("artifact1_slot") var artifact1Slot: String?,
    @JsonProperty("artifact2_slot") var artifact2Slot: String?,
    @JsonProperty("artifact3_slot") var artifact3Slot: String?,
    @JsonProperty("utility1_slot") var utility1Slot: String,
    @JsonProperty("utility1_slot_quantity") var utility1SlotQuantity: Int,
    @JsonProperty("utility2_slot") var utility2Slot: String,
    @JsonProperty("utility2_slot_quantity") var utility2SlotQuantity: Int,
    @JsonProperty("bag_slot") var bagSlot: String?,
    @JsonProperty("cooldown_expiration") var cooldownExpiration: Instant?
) {
    fun getLevelOf(job: String): Int {
        return when (job) {
            "mining" -> miningLevel
            "woodcutting" -> woodcuttingLevel
            "fishing" -> fishingLevel
            "cooking" -> cookingLevel
            "weaponcrafting" -> weaponcraftingLevel
            "jewelrycrafting" -> jewelrycraftingLevel
            "gearcrafting" -> gearcraftingLevel
            "alchemy" -> alchemyLevel
            else -> 0
        }
    }

    operator fun get(equipmentType: String): String? {
        return when (equipmentType) {
            "weapon_slot" -> weaponSlot
            "shield_slot" -> shieldSlot
            "helmet_slot" -> helmetSlot
            "body_armor_slot" -> bodyArmorSlot
            "leg_armor_slot" -> legArmorSlot
            "boots_slot" -> bootsSlot
            "ring1_slot" -> ring1Slot
            "ring2_slot" -> ring2Slot
            "amulet_slot" -> amuletSlot
            "artifact1_slot" -> artifact1Slot
            "artifact2_slot" -> artifact2Slot
            "artifact3_slot" -> artifact3Slot
            else -> null
        }
    }
}