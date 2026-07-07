package com.tellenn.artifacts.services.battlesim

import com.tellenn.artifacts.models.ArtifactsCharacter

object TestCharacters {
    fun blank(name: String = "hero"): ArtifactsCharacter = ArtifactsCharacter(
        name = name, account = "acc", level = 1, gold = 0, hp = 100, maxHp = 100,
        x = 0, y = 0, mapId = 0, layer = "overworld", inventory = emptyArray(), cooldown = 0,
        skin = null, task = null, initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0,
        criticalStrike = 0, speed = 0, haste = 0, xp = 0, maxXp = 0, taskType = null, taskTotal = 0,
        taskProgress = 0, miningXp = 0, miningMaxXp = 0, miningLevel = 1, woodcuttingXp = 0,
        woodcuttingMaxXp = 0, woodcuttingLevel = 1, fishingXp = 0, fishingMaxXp = 0, fishingLevel = 1,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 0, weaponcraftingLevel = 1, gearcraftingXp = 0,
        gearcraftingMaxXp = 0, gearcraftingLevel = 1, jewelrycraftingXp = 0, jewelrycraftingMaxXp = 0,
        jewelrycraftingLevel = 1, cookingXp = 0, cookingMaxXp = 0, cookingLevel = 1, alchemyXp = 0,
        alchemyMaxXp = 0, alchemyLevel = 1, inventoryMaxItems = 100, attackFire = 0, attackEarth = 0,
        attackWater = 0, attackAir = 0, dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0,
        resFire = 0, resEarth = 0, resWater = 0, resAir = 0, weaponSlot = null, runeSlot = null,
        shieldSlot = null, helmetSlot = null, bodyArmorSlot = null, legArmorSlot = null,
        bootsSlot = null, ring1Slot = null, ring2Slot = null, amuletSlot = null, artifact1Slot = null,
        artifact2Slot = null, artifact3Slot = null, utility1Slot = "", utility1SlotQuantity = 0,
        utility2Slot = "", utility2SlotQuantity = 0, bagSlot = null, cooldownExpiration = null,
    )
}
