package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BattleClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.CombatResponseBody
import com.tellenn.artifacts.clients.responses.SimulationResult
import com.tellenn.artifacts.exceptions.BattleLostException
import com.tellenn.artifacts.exceptions.CharacterInventoryFullException
import com.tellenn.artifacts.exceptions.UnknownMapException
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import com.tellenn.artifacts.utils.TimeUtils
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.InventorySlot
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.models.SimpleItem
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
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
    private lateinit var battleSimulatorService: BattleSimulatorService
    private lateinit var timeUtils: TimeUtils
    private lateinit var eventService: EventService
    private lateinit var battleService: BattleService

    /** Horloge de test mutable : permet de faire expirer le TTL du cache de faisabilité à volonté. */
    private var clock: Instant = Instant.parse("2026-07-06T12:00:00Z")

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
        battleSimulatorService = mock(BattleSimulatorService::class.java)
        timeUtils = mock(TimeUtils::class.java)
        eventService = mock(EventService::class.java)
        `when`(timeUtils.now()).thenAnswer { clock }
        battleService = BattleService(
            characterService, battleClient, monsterService, mapService, movementService,
            accountClient, equipmentService, bankService, bossFightService, battleSimulatorService,
            timeUtils, eventService
        )

        // Par défaut la simulation prédit une victoire nette et le meilleur stuff banque est vide
        `when`(equipmentService.findBestEquipmentForMonsterInBank(anyObject(), anyString(), anyInt()))
            .thenReturn(mutableMapOf())
        stubSimulation(losses = 0)

        // Par défaut : aucune potion en banque et un monstre neutre (jamais null en production)
        `when`(bankService.getHealingPotions()).thenReturn(emptyList())
        `when`(bankService.getOne(anyString())).thenReturn(SimpleItem("", 0))
        `when`(monsterService.getMonster(anyString())).thenReturn(mock(MonsterData::class.java))

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

    @Test
    fun `fightToGetItem jette BattleLostException sans combattre quand la simulation predit la defaite`() {
        // given — la simulation annonce plus d'une défaite sur 10
        stubSimulation(losses = 8)

        // when / then — aucun combat réel ni déplacement ne doit être tenté
        assertThrows(BattleLostException::class.java) {
            battleService.fightToGetItem(character(), "feather", 20)
        }
        verify(battleClient, never()).fight(anyString())
        verify(movementService, never()).moveCharacterToCell(anyInt(), anyObject())
    }

    @Test
    fun `isFightWinnable retourne true quand le stuff seul perd mais qu'une potion fait gagner`() {
        // given — le stuff seul perd (8 défaites), mais avec une potion équipée la simulation gagne
        stubSimulationWithPotions(lossesWithout = 8, lossesWith = 0)
        stubHealingPotionAvailable("minor_health_potion", level = 20)

        // when / then — la faisabilité doit refléter le loadout potions du combat réel
        assert(battleService.isFightWinnable(character(), "chicken"))
    }

    @Test
    fun `isFightWinnable reste false quand meme avec potions le combat est perdu`() {
        // given — même potions équipées, la simulation perd toujours
        stubSimulationWithPotions(lossesWithout = 8, lossesWith = 8)
        stubHealingPotionAvailable("minor_health_potion", level = 20)

        // when / then — combat vraiment ingagnable : on ne le tente pas
        assert(!battleService.isFightWinnable(character(), "chicken"))
    }

    @Test
    fun `isFightWinnable ne cherche pas de potion quand le stuff seul gagne`() {
        // given — victoire nette avec le stuff seul (défaut du setUp : losses = 0)

        // when
        battleService.isFightWinnable(character(), "chicken")

        // then — pas de second passage de simulation ni de lecture des potions banque
        verify(battleSimulatorService, times(1)).simulateWithApi(anyString(), anyObject<ArtifactsCharacter>())
        verify(bankService, never()).getHealingPotions()
    }

    @Test
    fun `isFightWinnable met en cache le verdict et ne re-simule pas dans la fenetre TTL`() {
        // given — victoire nette (défaut du setUp)

        // when — trois vérifications du même combat dans la même seconde
        repeat(3) { battleService.isFightWinnable(character(), "chicken") }

        // then — une seule simulation : les re-tests (recette + passes de boucle) sont mémoïsés
        verify(battleSimulatorService, times(1)).simulateWithApi(anyString(), anyObject<ArtifactsCharacter>())
    }

    @Test
    fun `isFightWinnable re-simule une fois le TTL expire`() {
        // given — un premier verdict est mis en cache
        battleService.isFightWinnable(character(), "chicken")

        // when — le temps avance au-delà du TTL (5 min) puis on re-teste
        clock = clock.plusSeconds(6 * 60)
        battleService.isFightWinnable(character(), "chicken")

        // then — le verdict périmé est recalculé
        verify(battleSimulatorService, times(2)).simulateWithApi(anyString(), anyObject<ArtifactsCharacter>())
    }

    @Test
    fun `isFightWinnable re-simule quand le personnage a change de niveau`() {
        // given — un verdict mis en cache au niveau 20
        battleService.isFightWinnable(character(), "chicken")

        // when — le même personnage revient un niveau plus haut
        battleService.isFightWinnable(character().copy(level = 21), "chicken")

        // then — un level-up doit invalider le verdict précédent
        verify(battleSimulatorService, times(2)).simulateWithApi(anyString(), anyObject<ArtifactsCharacter>())
    }

    @Test
    fun `fightToGetItem combat normalement quand la simulation predit la victoire`() {
        // given — victoire simulée (défaut du setUp) et un seul combat suffit
        `when`(battleClient.fight("Cloud"))
            .thenReturn(fightResponse(character(InventorySlot(1, "feather", 20))))

        // when
        battleService.fightToGetItem(character(), "feather", 20)

        // then
        verify(battleClient, times(1)).fight("Cloud")
    }

    @Test
    fun `isMonsterForItemOnMap renvoie true pour un monstre permanent sans requete live`() {
        // given — chicken n'est pas un monstre d'événement (défaut du setUp)

        // when / then — toujours présent sur la carte : aucune requête /maps live
        assert(battleService.isMonsterForItemOnMap("feather"))
        verify(monsterService, never()).findMonsterMapOrNull(anyString())
    }

    @Test
    fun `isMonsterForItemOnMap renvoie false quand le monstre d'evenement n'est pas apparu`() {
        // given — chicken est un monstre d'événement et aucune map live ne le contient
        `when`(eventService.isEventMonster("chicken")).thenReturn(true)
        `when`(monsterService.findMonsterMapOrNull("chicken")).thenReturn(null)

        // when / then
        assert(!battleService.isMonsterForItemOnMap("feather"))
    }

    @Test
    fun `isMonsterForItemOnMap renvoie true quand le monstre d'evenement est apparu`() {
        // given — l'événement est actif : le monstre est sur une map live
        `when`(eventService.isEventMonster("chicken")).thenReturn(true)
        `when`(monsterService.findMonsterMapOrNull("chicken")).thenReturn(mock(MapData::class.java))

        // when / then
        assert(battleService.isMonsterForItemOnMap("feather"))
    }

    @Test
    fun `fightToGetItem resout la map en direct pour un monstre d'evenement`() {
        // given — chicken est un monstre d'événement apparu : sa position vient de l'API live,
        // le cache local peut pointer sur une ancienne apparition
        `when`(eventService.isEventMonster("chicken")).thenReturn(true)
        val liveMap = mock(MapData::class.java)
        `when`(liveMap.mapId).thenReturn(7)
        `when`(mapService.findClosestMapFromApi(anyObject(), anyObject(), anyObject(), anyBoolean(), anyObject()))
            .thenReturn(liveMap)
        `when`(battleClient.fight("Cloud"))
            .thenReturn(fightResponse(character(InventorySlot(1, "feather", 20))))

        // when
        battleService.fightToGetItem(character(), "feather", 20)

        // then
        verify(mapService).findClosestMapFromApi(anyObject(), anyObject(), anyObject(), anyBoolean(), anyObject())
        verify(mapService, never()).findClosestMap(anyObject(), anyObject(), anyObject(), anyBoolean(), anyObject())
    }

    @Test
    fun `fightToGetItem propage UnknownMapException quand le monstre d'evenement n'est pas apparu`() {
        // given — l'événement est inactif : la résolution live ne trouve aucune map
        `when`(eventService.isEventMonster("chicken")).thenReturn(true)
        `when`(mapService.findClosestMapFromApi(anyObject(), anyObject(), anyObject(), anyBoolean(), anyObject()))
            .thenThrow(UnknownMapException(null, "chicken"))

        // when / then — le CrafterJob attrape UnknownMapException pour passer à l'item suivant
        assertThrows(UnknownMapException::class.java) {
            battleService.fightToGetItem(character(), "feather", 20)
        }
        verify(battleClient, never()).fight(anyString())
        verify(movementService, never()).moveCharacterToCell(anyInt(), anyObject())
    }

    private fun stubSimulation(losses: Int) {
        val simulation = SimulationResult(wins = 10 - losses, losses = losses, winrate = (10 - losses) * 10, results = emptyList())
        `when`(battleSimulatorService.simulateWithApi(anyString(), anyObject<ArtifactsCharacter>()))
            .thenReturn(ArtifactsResponseBody(simulation))
    }

    /** Simule [lossesWithout] défaites sans potion équipée, [lossesWith] dès qu'un slot utility est rempli. */
    private fun stubSimulationWithPotions(lossesWithout: Int, lossesWith: Int) {
        `when`(battleSimulatorService.simulateWithApi(anyString(), anyObject<ArtifactsCharacter>()))
            .thenAnswer { invocation ->
                val character = invocation.getArgument<ArtifactsCharacter>(1)
                val losses = if (character.utility1Slot != "" || character.utility2Slot != "") lossesWith else lossesWithout
                val simulation = SimulationResult(wins = 10 - losses, losses = losses, winrate = (10 - losses) * 10, results = emptyList())
                ArtifactsResponseBody(simulation)
            }
    }

    /** Rend disponible en banque une potion de soin de code [code] et de niveau [level]. */
    private fun stubHealingPotionAvailable(code: String, level: Int) {
        val potion = mock(ItemDetails::class.java)
        `when`(potion.code).thenReturn(code)
        `when`(potion.level).thenReturn(level)
        `when`(bankService.getHealingPotions()).thenReturn(listOf(potion))
        `when`(bankService.getOne(anyString())).thenReturn(SimpleItem("", 0))
        `when`(bankService.getOne(code)).thenReturn(SimpleItem(code, 50))
        `when`(monsterService.getMonster(anyString())).thenReturn(mock(MonsterData::class.java))
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
