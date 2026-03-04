package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@Suppress("unused")
class ArtifactsCharacter(
    var name: String,
    var account: String,
    var level: Int,
    var gold: Int,
    var hp: Int,
    @param:JsonProperty("max_hp") var maxHp: Int,
    var x: Int,
    var y: Int,
    @param:JsonProperty("map_id") var mapId: Int,
    var layer: String,
    var inventory: Array<InventorySlot>,
    var cooldown: Int,
    var skin: String?,
    var task: String?,
    var initiative: Int,
    var threat: Int,
    @param:JsonProperty("dmg") var dmg: Int,
    @param:JsonProperty("wisdom") var wisdom: Int,
    @param:JsonProperty("prospecting") var prospecting: Int,
    @param:JsonProperty("critical_strike") var criticalStrike: Int,
    @param:JsonProperty("speed") var speed: Int,
    @param:JsonProperty("haste") var haste: Int,
    @param:JsonProperty("xp") var xp: Int,
    @param:JsonProperty("max_xp") var maxXp: Int,
    @param:JsonProperty("task_type") var taskType: String?,
    @param:JsonProperty("task_total") var taskTotal: Int,
    @param:JsonProperty("task_progress") var taskProgress: Int,
    @param:JsonProperty("mining_xp") var miningXp: Int,
    @param:JsonProperty("mining_max_xp") var miningMaxXp: Int,
    @param:JsonProperty("mining_level") var miningLevel: Int,
    @param:JsonProperty("woodcutting_xp") var woodcuttingXp: Int,
    @param:JsonProperty("woodcutting_max_xp") var woodcuttingMaxXp: Int,
    @param:JsonProperty("woodcutting_level") var woodcuttingLevel: Int,
    @param:JsonProperty("fishing_xp") var fishingXp: Int,
    @param:JsonProperty("fishing_max_xp") var fishingMaxXp: Int,
    @param:JsonProperty("fishing_level") var fishingLevel: Int,
    @param:JsonProperty("weaponcrafting_xp") var weaponcraftingXp: Int,
    @param:JsonProperty("weaponcrafting_max_xp") var weaponcraftingMaxXp: Int,
    @param:JsonProperty("weaponcrafting_level") var weaponcraftingLevel: Int,
    @param:JsonProperty("gearcrafting_xp") var gearcraftingXp: Int,
    @param:JsonProperty("gearcrafting_max_xp") var gearcraftingMaxXp: Int,
    @param:JsonProperty("gearcrafting_level") var gearcraftingLevel: Int,
    @param:JsonProperty("jewelrycrafting_xp") var jewelrycraftingXp: Int,
    @param:JsonProperty("jewelrycrafting_max_xp") var jewelrycraftingMaxXp: Int,
    @param:JsonProperty("jewelrycrafting_level") var jewelrycraftingLevel: Int,
    @param:JsonProperty("cooking_xp") var cookingXp: Int,
    @param:JsonProperty("cooking_max_xp") var cookingMaxXp: Int,
    @param:JsonProperty("cooking_level") var cookingLevel: Int,
    @param:JsonProperty("alchemy_xp") var alchemyXp: Int,
    @param:JsonProperty("alchemy_max_xp") var alchemyMaxXp: Int,
    @param:JsonProperty("alchemy_level") var alchemyLevel: Int,
    @param:JsonProperty("inventory_max_items") var inventoryMaxItems: Int,
    @param:JsonProperty("attack_fire") var attackFire: Int,
    @param:JsonProperty("attack_earth") var attackEarth: Int,
    @param:JsonProperty("attack_water") var attackWater: Int,
    @param:JsonProperty("attack_air") var attackAir: Int,
    @param:JsonProperty("dmg_fire") var dmgFire: Int,
    @param:JsonProperty("dmg_earth") var dmgEarth: Int,
    @param:JsonProperty("dmg_water") var dmgWater: Int,
    @param:JsonProperty("dmg_air") var dmgAir: Int,
    @param:JsonProperty("res_fire") var resFire: Int,
    @param:JsonProperty("res_earth") var resEarth: Int,
    @param:JsonProperty("res_water") var resWater: Int,
    @param:JsonProperty("res_air") var resAir: Int,
    @param:JsonProperty("weapon_slot") var weaponSlot: String?,
    @param:JsonProperty("rune_slot") var runeSlot: String?,
    @param:JsonProperty("shield_slot") var shieldSlot: String?,
    @param:JsonProperty("helmet_slot") var helmetSlot: String?,
    @param:JsonProperty("body_armor_slot") var bodyArmorSlot: String?,
    @param:JsonProperty("leg_armor_slot") var legArmorSlot: String?,
    @param:JsonProperty("boots_slot") var bootsSlot: String?,
    @param:JsonProperty("ring1_slot") var ring1Slot: String?,
    @param:JsonProperty("ring2_slot") var ring2Slot: String?,
    @param:JsonProperty("amulet_slot") var amuletSlot: String?,
    @param:JsonProperty("artifact1_slot") var artifact1Slot: String?,
    @param:JsonProperty("artifact2_slot") var artifact2Slot: String?,
    @param:JsonProperty("artifact3_slot") var artifact3Slot: String?,
    @param:JsonProperty("utility1_slot") var utility1Slot: String,
    @param:JsonProperty("utility1_slot_quantity") var utility1SlotQuantity: Int,
    @param:JsonProperty("utility2_slot") var utility2Slot: String,
    @param:JsonProperty("utility2_slot_quantity") var utility2SlotQuantity: Int,
    @param:JsonProperty("bag_slot") var bagSlot: String?,
    @param:JsonProperty("cooldown_expiration") var cooldownExpiration: Instant?,
    var effects: List<Effect> = emptyList()
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