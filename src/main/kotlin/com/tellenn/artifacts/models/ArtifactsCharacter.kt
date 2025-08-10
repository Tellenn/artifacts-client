package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.AppConfig
import java.time.Instant
import kotlin.math.min

@Suppress("unused")
class ArtifactsCharacter(
    val name: String,
    val account: String,
    val level: Int,
    val gold: Int,
    val hp: Int,
    @JsonProperty("max_hp") val maxHp: Int,
    val x: Int,
    val y: Int,
    val inventory: Array<InventorySlot>,
    val cooldown: Int,
    val skin: String?,
    val task: String?,
    @JsonProperty("dmg") val dmg: Int,
    @JsonProperty("wisdom") val wisdom: Int,
    @JsonProperty("prospecting") val prospecting: Int,
    @JsonProperty("critical_strike") val criticalStrike: Int,
    @JsonProperty("speed") val speed: Int,
    @JsonProperty("haste") val haste: Int,
    @JsonProperty("xp") val xp: Int,
    @JsonProperty("max_xp") val maxXp: Int,
    @JsonProperty("task_type") val taskType: String?,
    @JsonProperty("task_total") val taskTotal: Int,
    @JsonProperty("task_progress") val taskProgress: Int,
    @JsonProperty("mining_xp") val miningXp: Int,
    @JsonProperty("mining_max_xp") val miningMaxXp: Int,
    @JsonProperty("mining_level") val miningLevel: Int,
    @JsonProperty("woodcutting_xp") val woodcuttingXp: Int,
    @JsonProperty("woodcutting_max_xp") val woodcuttingMaxXp: Int,
    @JsonProperty("woodcutting_level") val woodcuttingLevel: Int,
    @JsonProperty("fishing_xp") val fishingXp: Int,
    @JsonProperty("fishing_max_xp") val fishingMaxXp: Int,
    @JsonProperty("fishing_level") val fishingLevel: Int,
    @JsonProperty("weaponcrafting_xp") val weaponcraftingXp: Int,
    @JsonProperty("weaponcrafting_max_xp") val weaponcraftingMaxXp: Int,
    @JsonProperty("weaponcrafting_level") val weaponcraftingLevel: Int,
    @JsonProperty("gearcrafting_xp") val gearcraftingXp: Int,
    @JsonProperty("gearcrafting_max_xp") val gearcraftingMaxXp: Int,
    @JsonProperty("gearcrafting_level") val gearcraftingLevel: Int,
    @JsonProperty("jewelrycrafting_xp") val jewelrycraftingXp: Int,
    @JsonProperty("jewelrycrafting_max_xp") val jewelrycraftingMaxXp: Int,
    @JsonProperty("jewelrycrafting_level") val jewelrycraftingLevel: Int,
    @JsonProperty("cooking_xp") val cookingXp: Int,
    @JsonProperty("cooking_max_xp") val cookingMaxXp: Int,
    @JsonProperty("cooking_level") val cookingLevel: Int,
    @JsonProperty("alchemy_xp") val alchemyXp: Int,
    @JsonProperty("alchemy_max_xp") val alchemyMaxXp: Int,
    @JsonProperty("alchemy_level") val alchemyLevel: Int,
    @JsonProperty("inventory_max_items") val inventoryMaxItems: Int,
    @JsonProperty("attack_fire") val attackFire: Int,
    @JsonProperty("attack_earth") val attackEarth: Int,
    @JsonProperty("attack_water") val attackWater: Int,
    @JsonProperty("attack_air") val attackAir: Int,
    @JsonProperty("dmg_fire") val dmgFire: Int,
    @JsonProperty("dmg_earth") val dmgEarth: Int,
    @JsonProperty("dmg_water") val dmgWater: Int,
    @JsonProperty("dmg_air") val dmgAir: Int,
    @JsonProperty("res_fire") val resFire: Int,
    @JsonProperty("res_earth") val resEarth: Int,
    @JsonProperty("res_water") val resWater: Int,
    @JsonProperty("res_air") val resAir: Int,
    @JsonProperty("weapon_slot") val weaponSlot: String?,
    @JsonProperty("rune_slot") val runeSlot: String?,
    @JsonProperty("shield_slot") val shieldSlot: String?,
    @JsonProperty("helmet_slot") val helmetSlot: String?,
    @JsonProperty("body_armor_slot") val bodyArmorSlot: String?,
    @JsonProperty("leg_armor_slot") val legArmorSlot: String?,
    @JsonProperty("boots_slot") val bootsSlot: String?,
    @JsonProperty("ring1_slot") val ring1Slot: String?,
    @JsonProperty("ring2_slot") val ring2Slot: String?,
    @JsonProperty("amulet_slot") val amuletSlot: String?,
    @JsonProperty("artifact1_slot") val artifact1Slot: String?,
    @JsonProperty("artifact2_slot") val artifact2Slot: String?,
    @JsonProperty("artifact3_slot") val artifact3Slot: String?,
    @JsonProperty("utility1_slot") val utility1Slot: String?,
    @JsonProperty("utility1_slot_quantity") val utility1SlotQuantity: Int,
    @JsonProperty("utility2_slot") val utility2Slot: String?,
    @JsonProperty("utility2_slot_quantity") val utility2SlotQuantity: Int,
    @JsonProperty("bag_slot") val bagSlot: String?,
    @JsonProperty("cooldown_expiration") val cooldownExpiration: Instant?
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