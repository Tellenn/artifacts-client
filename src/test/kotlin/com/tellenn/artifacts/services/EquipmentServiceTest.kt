package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Effect
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant

class EquipmentServiceTest {

    private lateinit var bankService: BankService
    private lateinit var itemRepository: ItemRepository
    private lateinit var service: EquipmentService

    @BeforeEach
    fun setUp() {
        bankService = mock(BankService::class.java)
        itemRepository = mock(ItemRepository::class.java)
        service = EquipmentService(
            bankService = bankService,
            monsterService = mock(MonsterService::class.java),
            itemRepository = itemRepository,
            characterService = mock(CharacterService::class.java),
            itemService = mock(ItemService::class.java),
            movementService = mock(MovementService::class.java),
            battleSimulatorService = mock(BattleSimulatorService::class.java),
            bankItemSyncService = mock(BankItemSyncService::class.java),
            accountClient = mock(AccountClient::class.java),
            merchantService = mock(MerchantService::class.java),
        )
    }

    @Test
    fun `TANK picks the resistance potion on the boss strongest attack element`() {
        // given — boss hits hardest with fire
        val monster = monster(attackFire = 200, attackEarth = 50, attackWater = 10, attackAir = 0)
        val fireRes = potion("fire_res_potion", level = 10, effect("boost_res_fire", 30))
        val earthRes = potion("earth_res_potion", level = 10, effect("boost_res_earth", 40))
        `when`(bankService.getCombatPotions()).thenReturn(listOf(fireRes, earthRes))

        // when
        val loadout = service.selectBossPotions(character(level = 20), monster, BossRole.TANK)

        // then
        assertEquals("fire_res_potion", loadout.utility1)
    }

    @Test
    fun `DPS picks the damage potion on the character strongest weapon element`() {
        // given — equipped weapon is mostly an air weapon
        val monster = monster(attackFire = 100)
        val airWeapon = ItemDetails(
            code = "air_sword", name = "Air Sword", description = "", type = "weapon", subtype = "",
            level = 20, tradeable = true, craft = null,
            effects = listOf(effect("attack_air", 80), effect("attack_fire", 20)), conditions = null,
        )
        `when`(itemRepository.findByCode("air_sword")).thenReturn(airWeapon)
        val airBoost = potion("air_boost_potion", level = 10, effect("boost_dmg_air", 25))
        val fireBoost = potion("fire_boost_potion", level = 10, effect("boost_dmg_fire", 25))
        `when`(bankService.getCombatPotions()).thenReturn(listOf(airBoost, fireBoost))

        // when
        val loadout = service.selectBossPotions(character(level = 20, weaponSlot = "air_sword"), monster, BossRole.DPS)

        // then
        assertEquals("air_boost_potion", loadout.utility1)
    }

    @Test
    fun `utility1 is null when the ideal potion is absent from the bank`() {
        // given — boss hits hardest with water but only a fire resistance potion is banked
        val monster = monster(attackWater = 300)
        val fireRes = potion("fire_res_potion", level = 10, effect("boost_res_fire", 30))
        `when`(bankService.getCombatPotions()).thenReturn(listOf(fireRes))

        // when
        val loadout = service.selectBossPotions(character(level = 20), monster, BossRole.TANK)

        // then
        assertNull(loadout.utility1)
    }

    @Test
    fun `utility2 is the strongest healing potion usable at the character level`() {
        // given
        val monster = monster(attackFire = 100)
        val small = potion("small_health_potion", level = 5, effect("restore", 60))
        val greater = potion("greater_health_potion", level = 15, effect("restore", 150))
        val tooHigh = potion("enchanted_health_potion", level = 35, effect("restore", 400))
        `when`(bankService.getCombatPotions()).thenReturn(listOf(small, greater, tooHigh))

        // when
        val loadout = service.selectBossPotions(character(level = 20), monster, BossRole.DPS)

        // then — strongest restore at or below level 20
        assertEquals("greater_health_potion", loadout.utility2)
    }

    private fun effect(code: String, value: Int) = Effect(code, value, null)

    private fun potion(code: String, level: Int, vararg effects: Effect) = ItemDetails(
        code = code, name = code, description = "", type = "consumable", subtype = "potion",
        level = level, tradeable = true, craft = null, effects = effects.toList(), conditions = null,
    )

    private fun monster(
        attackFire: Int = 0,
        attackEarth: Int = 0,
        attackWater: Int = 0,
        attackAir: Int = 0,
    ) = MonsterData(
        name = "Boss", code = "boss", level = 40, hp = 5000,
        attackFire = attackFire, attackEarth = attackEarth, attackWater = attackWater, attackAir = attackAir,
        defenseFire = 0, defenseEarth = 0, defenseWater = 0, defenseAir = 0,
        criticalStrike = 0, effects = emptyList(), minGold = 0, maxGold = 0, drops = null,
        initiative = 0, type = "boss",
    )

    private fun character(level: Int, weaponSlot: String? = null) = ArtifactsCharacter(
        name = "Hero", account = "acc", level = level, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = arrayOf(), cooldown = 0, skin = null, task = null,
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0, criticalStrike = 0, speed = 0,
        haste = 0, xp = 0, maxXp = 100, taskType = null, taskTotal = 0, taskProgress = 0,
        miningXp = 0, miningMaxXp = 100, miningLevel = 1, woodcuttingXp = 0, woodcuttingMaxXp = 100,
        woodcuttingLevel = 1, fishingXp = 0, fishingMaxXp = 100, fishingLevel = 1,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 100, weaponcraftingLevel = 1,
        gearcraftingXp = 0, gearcraftingMaxXp = 100, gearcraftingLevel = 1,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 100, jewelrycraftingLevel = 1,
        cookingXp = 0, cookingMaxXp = 100, cookingLevel = 1, alchemyXp = 0, alchemyMaxXp = 100, alchemyLevel = 1,
        inventoryMaxItems = 100, attackFire = 0, attackEarth = 0, attackWater = 0, attackAir = 0,
        dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0, resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
        weaponSlot = weaponSlot, runeSlot = null, shieldSlot = null, helmetSlot = null, bodyArmorSlot = null,
        legArmorSlot = null, bootsSlot = null, ring1Slot = null, ring2Slot = null, amuletSlot = null,
        artifact1Slot = null, artifact2Slot = null, artifact3Slot = null,
        utility1Slot = "", utility1SlotQuantity = 0, utility2Slot = "", utility2SlotQuantity = 0,
        bagSlot = null, cooldownExpiration = Instant.now(),
    )
}
