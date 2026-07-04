package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BattleClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.CombatResponseBody
import com.tellenn.artifacts.exceptions.CharacterInventoryFullException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.InventorySlot
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.models.MonsterData
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Helpers null-safe pour les matchers Mockito sur des paramètres Kotlin non-nullables
 * (même pattern que GatheringWorkerServiceTest).
 */
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

class BattleServiceTest {

    private lateinit var characterService: CharacterService
    private lateinit var battleClient: BattleClient
    private lateinit var monsterService: MonsterService
    private lateinit var mapService: MapService
    private lateinit var movementService: MovementService
    private lateinit var accountClient: AccountClient
    private lateinit var equipmentService: EquipmentService
    private lateinit var bankService: BankService
    private lateinit var bossFightService: BossFightService
    private lateinit var battleService: BattleService

    @BeforeEach
    fun setUp() {
        characterService = mock(CharacterService::class.java)
        battleClient = mock(BattleClient::class.java)
        monsterService = mock(MonsterService::class.java)
        mapService = mock(MapService::class.java)
        movementService = mock(MovementService::class.java)
        accountClient = mock(AccountClient::class.java)
        equipmentService = mock(EquipmentService::class.java)
        bankService = mock(BankService::class.java)
        bossFightService = mock(BossFightService::class.java)
        battleService = BattleService(
            characterService, battleClient, monsterService, mapService, movementService,
            accountClient, equipmentService, bankService, bossFightService
        )

        val monster = mock(MonsterData::class.java)
        `when`(monster.code).thenReturn("chicken")
        `when`(monster.type).thenReturn("normal")
        `when`(monsterService.findMonsterThatDrop("feather")).thenReturn(monster)

        val map = mock(MapData::class.java)
        `when`(map.mapId).thenReturn(42)
        `when`(mapService.findClosestMap(anyObject(), anyObject(), anyObject(), anyBoolean(), anyObject()))
            .thenReturn(map)

        // Les collaborateurs de déplacement/équipement rendent le personnage qu'on leur passe
        `when`(equipmentService.equipBestAvailableEquipmentForMonsterInBank(anyObject(), anyString(), anyInt(), anyBoolean()))
            .thenAnswer { it.getArgument(0) }
        `when`(movementService.moveCharacterToCell(anyInt(), anyObject())).thenAnswer { it.getArgument(1) }
        `when`(movementService.moveToBank(anyObject(), anyBoolean())).thenAnswer { it.getArgument(0) }
        // Le dépôt en banque vide l'inventaire
        `when`(bankService.emptyInventory(anyObject())).thenAnswer { character() }

        // has() reproduit la sémantique réelle : quantité de l'item dans l'inventaire
        `when`(characterService.has(anyObject(), anyInt(), anyString())).thenAnswer { invocation ->
            val character = invocation.getArgument<ArtifactsCharacter>(0)
            val quantity = invocation.getArgument<Int>(1)
            val code = invocation.getArgument<String>(2)
            character.inventory.filter { it.code == code }.sumOf { it.quantity } >= quantity
        }
    }

    @Test
    fun `fightToGetItem ne re-farme pas ce qui a deja ete banque apres un debordement d'inventaire`() {
        // given : 12 feathers déjà en poche sur une cible de 20, et l'inventaire déborde (497)
        // au combat suivant ; après le passage en banque, il ne doit en manquer que 8
        val start = character(InventorySlot(1, "feather", 12))
        `when`(battleClient.fight("Cloud"))
            .thenThrow(CharacterInventoryFullException("API Error 497"))
            .thenReturn(fightResponse(character(InventorySlot(1, "feather", 8))))
            .thenReturn(fightResponse(character(InventorySlot(1, "feather", 20))))

        // when
        battleService.fightToGetItem(start, "feather", 20)

        // then : 1 combat déborde + 1 combat pour le solde de 8 — sans comptage cumulatif,
        // le personnage re-farmerait 20 feathers depuis zéro (3e combat)
        verify(battleClient, times(2)).fight("Cloud")
    }

    private fun fightResponse(character: ArtifactsCharacter) =
        ArtifactsResponseBody(CombatResponseBody(mock(Cooldown::class.java), listOf(character), null))

    private fun character(vararg slots: InventorySlot) = ArtifactsCharacter(
        name = "Cloud", account = "tellenn", level = 20, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = arrayOf(*slots), cooldown = 0, skin = null, task = null,
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
        weaponSlot = null, runeSlot = null, shieldSlot = null, helmetSlot = null, bodyArmorSlot = null,
        legArmorSlot = null, bootsSlot = null, ring1Slot = null, ring2Slot = null, amuletSlot = null,
        artifact1Slot = null, artifact2Slot = null, artifact3Slot = null,
        utility1Slot = "", utility1SlotQuantity = 0, utility2Slot = "", utility2SlotQuantity = 0,
        bagSlot = null, cooldownExpiration = Instant.now(),
    )
}
