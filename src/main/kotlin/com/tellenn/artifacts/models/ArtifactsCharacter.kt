package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonAlias
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
    @JsonAlias("max_hp") val maxHp: Int,
    val x: Int,
    val y: Int,
    val inventory: Array<InventorySlot>,
    val cooldown: Int,
    val skin: String?,
    val task: String?,
    @JsonAlias("dmg") val dmg: Int,
    @JsonAlias("wisdom") val wisdom: Int,
    @JsonAlias("prospecting") val prospecting: Int,
    @JsonAlias("critical_strike") val criticalStrike: Int,
    @JsonAlias("speed") val speed: Int,
    @JsonAlias("haste") val haste: Int,
    @JsonAlias("xp") val xp: Int,
    @JsonAlias("max_xp") val maxXp: Int,
    @JsonAlias("task_type") val taskType: String?,
    @JsonAlias("task_total") val taskTotal: Int,
    @JsonAlias("task_progress") val taskProgress: Int,
    @JsonAlias("mining_xp") val miningXp: Int,
    @JsonAlias("mining_max_xp") val miningMaxXp: Int,
    @JsonAlias("mining_level") val miningLevel: Int,
    @JsonAlias("woodcutting_xp") val woodcuttingXp: Int,
    @JsonAlias("woodcutting_max_xp") val woodcuttingMaxXp: Int,
    @JsonAlias("woodcutting_level") val woodcuttingLevel: Int,
    @JsonAlias("fishing_xp") val fishingXp: Int,
    @JsonAlias("fishing_max_xp") val fishingMaxXp: Int,
    @JsonAlias("fishing_level") val fishingLevel: Int,
    @JsonAlias("weaponcrafting_xp") val weaponcraftingXp: Int,
    @JsonAlias("weaponcrafting_max_xp") val weaponcraftingMaxXp: Int,
    @JsonAlias("weaponcrafting_level") val weaponcraftingLevel: Int,
    @JsonAlias("gearcrafting_xp") val gearcraftingXp: Int,
    @JsonAlias("gearcrafting_max_xp") val gearcraftingMaxXp: Int,
    @JsonAlias("gearcrafting_level") val gearcraftingLevel: Int,
    @JsonAlias("jewelrycrafting_xp") val jewelrycraftingXp: Int,
    @JsonAlias("jewelrycrafting_max_xp") val jewelrycraftingMaxXp: Int,
    @JsonAlias("jewelrycrafting_level") val jewelrycraftingLevel: Int,
    @JsonAlias("cooking_xp") val cookingXp: Int,
    @JsonAlias("cooking_max_xp") val cookingMaxXp: Int,
    @JsonAlias("cooking_level") val cookingLevel: Int,
    @JsonAlias("alchemy_xp") val alchemyXp: Int,
    @JsonAlias("alchemy_max_xp") val alchemyMaxXp: Int,
    @JsonAlias("alchemy_level") val alchemyLevel: Int,
    @JsonAlias("inventory_max_items") val inventoryMaxItems: Int,
    @JsonAlias("attack_fire") val attackFire: Int,
    @JsonAlias("attack_earth") val attackEarth: Int,
    @JsonAlias("attack_water") val attackWater: Int,
    @JsonAlias("attack_air") val attackAir: Int,
    @JsonAlias("dmg_fire") val dmgFire: Int,
    @JsonAlias("dmg_earth") val dmgEarth: Int,
    @JsonAlias("dmg_water") val dmgWater: Int,
    @JsonAlias("dmg_air") val dmgAir: Int,
    @JsonAlias("res_fire") val resFire: Int,
    @JsonAlias("res_earth") val resEarth: Int,
    @JsonAlias("res_water") val resWater: Int,
    @JsonAlias("res_air") val resAir: Int,
    @JsonAlias("weapon_slot") val weaponSlot: String?,
    @JsonAlias("rune_slot") val runeSlot: String?,
    @JsonAlias("shield_slot") val shieldSlot: String?,
    @JsonAlias("helmet_slot") val helmetSlot: String?,
    @JsonAlias("body_armor_slot") val bodyArmorSlot: String?,
    @JsonAlias("leg_armor_slot") val legArmorSlot: String?,
    @JsonAlias("boots_slot") val bootsSlot: String?,
    @JsonAlias("ring1_slot") val ring1Slot: String?,
    @JsonAlias("ring2_slot") val ring2Slot: String?,
    @JsonAlias("amulet_slot") val amuletSlot: String?,
    @JsonAlias("artifact1_slot") val artifact1Slot: String?,
    @JsonAlias("artifact2_slot") val artifact2Slot: String?,
    @JsonAlias("artifact3_slot") val artifact3Slot: String?,
    @JsonAlias("utility1_slot") val utility1Slot: String?,
    @JsonAlias("utility1_slot_quantity") val utility1SlotQuantity: Int,
    @JsonAlias("utility2_slot") val utility2Slot: String?,
    @JsonAlias("utility2_slot_quantity") val utility2SlotQuantity: Int,
    @JsonAlias("bag_slot") val bagSlot: String?,
    @JsonAlias("cooldown_expiration") val cooldownExpiration: Instant?
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