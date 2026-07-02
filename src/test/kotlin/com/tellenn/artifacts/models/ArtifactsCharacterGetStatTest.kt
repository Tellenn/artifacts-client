package com.tellenn.artifacts.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtifactsCharacterGetStatTest {

    private fun buildCharacter(
        level: Int = 0,
        miningLevel: Int = 0,
        woodcuttingLevel: Int = 0,
        fishingLevel: Int = 0,
        cookingLevel: Int = 0,
        weaponcraftingLevel: Int = 0,
        gearcraftingLevel: Int = 0,
        jewelrycraftingLevel: Int = 0,
        alchemyLevel: Int = 0,
        weaponcraftingXp: Int = 0,
        miningXp: Int = 0,
        hp: Int = 0,
        maxHp: Int = 0,
    ): ArtifactsCharacter = ArtifactsCharacter(
        name = "Test", account = "test", level = level, gold = 0,
        hp = hp, maxHp = maxHp, x = 0, y = 0, mapId = 0, layer = "main",
        inventory = emptyArray(), cooldown = 0, skin = null, task = null,
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0,
        criticalStrike = 0, speed = 0, haste = 0, xp = 0, maxXp = 0,
        taskType = null, taskTotal = 0, taskProgress = 0,
        miningXp = miningXp, miningMaxXp = 0, miningLevel = miningLevel,
        woodcuttingXp = 0, woodcuttingMaxXp = 0, woodcuttingLevel = woodcuttingLevel,
        fishingXp = 0, fishingMaxXp = 0, fishingLevel = fishingLevel,
        weaponcraftingXp = weaponcraftingXp, weaponcraftingMaxXp = 0, weaponcraftingLevel = weaponcraftingLevel,
        gearcraftingXp = 0, gearcraftingMaxXp = 0, gearcraftingLevel = gearcraftingLevel,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 0, jewelrycraftingLevel = jewelrycraftingLevel,
        cookingXp = 0, cookingMaxXp = 0, cookingLevel = cookingLevel,
        alchemyXp = 0, alchemyMaxXp = 0, alchemyLevel = alchemyLevel,
        inventoryMaxItems = 100,
        attackFire = 0, attackEarth = 0, attackWater = 0, attackAir = 0,
        dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0,
        resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
        weaponSlot = null, runeSlot = null, shieldSlot = null, helmetSlot = null,
        bodyArmorSlot = null, legArmorSlot = null, bootsSlot = null,
        ring1Slot = null, ring2Slot = null, amuletSlot = null,
        artifact1Slot = null, artifact2Slot = null, artifact3Slot = null,
        utility1Slot = "", utility1SlotQuantity = 0,
        utility2Slot = "", utility2SlotQuantity = 0,
        bagSlot = null, cooldownExpiration = null,
    )

    @Test
    fun `getStat returns level`() {
        assertEquals(25, buildCharacter(level = 25).getStat("level"))
    }

    @Test
    fun `getStat returns mining_level`() {
        assertEquals(30, buildCharacter(miningLevel = 30).getStat("mining_level"))
    }

    @Test
    fun `getStat returns alchemy_level`() {
        assertEquals(15, buildCharacter(alchemyLevel = 15).getStat("alchemy_level"))
    }

    @Test
    fun `getStat returns hp`() {
        assertEquals(80, buildCharacter(hp = 80).getStat("hp"))
    }

    @Test
    fun `getStat returns 0 for unknown code`() {
        assertEquals(0, buildCharacter().getStat("unknown_code"))
    }

    @Test
    fun `getXpOf returns the skill current xp`() {
        assertEquals(1234, buildCharacter(weaponcraftingXp = 1234).getXpOf("weaponcrafting"))
        assertEquals(42, buildCharacter(miningXp = 42).getXpOf("mining"))
    }

    @Test
    fun `getXpOf returns 0 for unknown skill`() {
        assertEquals(0, buildCharacter().getXpOf("unknown_skill"))
    }
}
