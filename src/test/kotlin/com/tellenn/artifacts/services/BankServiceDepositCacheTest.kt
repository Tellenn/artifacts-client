package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.BankItemTransaction
import com.tellenn.artifacts.clients.responses.MovementResponseBody
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.exceptions.MapContentNotFoundException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.argThat
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Helpers null-safe pour les matchers Mockito sur des paramètres Kotlin non-nullables
 * (même approche que BankServiceTeleportTest).
 */
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

private fun <T> eqObject(value: T): T {
    eq(value)
    return uninitialized()
}

private fun argMatchDoc(predicate: (BankItemDocument) -> Boolean): BankItemDocument {
    argThat<BankItemDocument> { predicate(it) }
    return uninitialized()
}

class BankServiceDepositCacheTest {

    private val bankClient = mock(BankClient::class.java)
    private val bankRepository = mock(BankItemRepository::class.java)
    private val itemRepository = mock(ItemRepository::class.java)
    private val characterService = mock(CharacterService::class.java)
    private val bankItemSyncService = mock(BankItemSyncService::class.java)
    private val accountClient = mock(AccountClient::class.java)
    private val movementClient = mock(MovementClient::class.java)
    private val mapService = mock(MapService::class.java)
    private val teleportService = mock(TeleportService::class.java)

    private val service = BankService(
        bankClient, bankRepository, itemRepository, characterService,
        bankItemSyncService, accountClient, movementClient, mapService, teleportService
    )

    init {
        // Spring Data déclare save() non-null (@NonNullApi) : Kotlin vérifie le retour à l'exécution,
        // le null par défaut du mock ferait donc un NPE — on renvoie l'argument comme le vrai repo.
        `when`(bankRepository.save(anyObject<BankItemDocument>())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `deposit restaure l'entrée cache existante avant de re-tenter après une désync de position`() {
        // given — 5 old_boots au cache, on en dépose 3 ; la 1re tentative échoue (pas sur la banque),
        // la 2e réussit après déplacement. Sans rollback, la récursion recompte les 3 bottes deux fois.
        val character = buildCharacter()
        val successResponse = depositResponse(character)
        `when`(itemRepository.findByCode("old_boots")).thenReturn(boots())
        `when`(bankRepository.findByCode("old_boots")).thenReturn(bankedBoots(quantity = 5))
        `when`(bankClient.depositItems(eqObject("Renoir"), anyObject()))
            .thenThrow(MapContentNotFoundException())
            .thenReturn(successResponse)
        stubBankRelocation(character)

        // when
        service.deposit(character, listOf(SimpleItem("old_boots", 3)))

        // then — l'entrée d'origine (quantité 5) est restaurée avant la re-tentative
        verify(bankRepository, atLeastOnce())
            .save(argMatchDoc { it.code == "old_boots" && it.quantity == 5 })
    }

    @Test
    fun `deposit supprime l'entrée cache insérée avant de re-tenter après une désync de position`() {
        // given — old_boots absent du cache : la 1re passe insère une entrée, puis l'API échoue
        // (pas sur la banque). Sans suppression, la récursion insère une seconde entrée fantôme.
        val character = buildCharacter()
        val successResponse = depositResponse(character)
        `when`(itemRepository.findByCode("old_boots")).thenReturn(boots())
        `when`(bankRepository.findByCode("old_boots")).thenReturn(null)
        `when`(bankClient.depositItems(eqObject("Renoir"), anyObject()))
            .thenThrow(MapContentNotFoundException())
            .thenReturn(successResponse)
        stubBankRelocation(character)

        // when
        service.deposit(character, listOf(SimpleItem("old_boots", 3)))

        // then — l'entrée insérée est retirée du cache avant la re-tentative
        verify(bankRepository, atLeastOnce()).delete(argMatchDoc { it.code == "old_boots" })
    }

    @Test
    fun `deposit ne se redeplace pas quand le personnage resynchronise est deja a la banque`() {
        // given — 598 à la 1re tentative, mais l'état serveur montre le perso déjà sur la banque
        // (545) : re-déplacer déclencherait un 490 « character already at destination ».
        val character = buildCharacter()
        val successResponse = depositResponse(character)
        val accountResponse = characterResponse(character)
        val bankMap = mock(MapData::class.java)
        `when`(bankMap.mapId).thenReturn(545) // même case que le personnage
        `when`(itemRepository.findByCode("old_boots")).thenReturn(boots())
        `when`(bankRepository.findByCode("old_boots")).thenReturn(bankedBoots(quantity = 5))
        `when`(bankClient.depositItems(eqObject("Renoir"), anyObject()))
            .thenThrow(MapContentNotFoundException())
            .thenReturn(successResponse)
        `when`(accountClient.getCharacter("Renoir")).thenReturn(accountResponse)
        `when`(mapService.findClosestMap(anyObject(), any(), any(), anyBoolean(), anyObject()))
            .thenReturn(bankMap)

        // when
        service.deposit(character, listOf(SimpleItem("old_boots", 3)))

        // then — aucun déplacement : le dépôt est simplement re-tenté sur place
        verify(movementClient, never()).move(anyString(), anyInt())
    }

    private fun stubBankRelocation(character: ArtifactsCharacter) {
        val accountResponse = characterResponse(character)
        val movementResponse = moveResponse(character)
        val bankMap = mock(MapData::class.java)
        `when`(bankMap.mapId).thenReturn(955)
        `when`(accountClient.getCharacter("Renoir")).thenReturn(accountResponse)
        `when`(mapService.findClosestMap(anyObject(), any(), any(), anyBoolean(), anyObject()))
            .thenReturn(bankMap)
        `when`(movementClient.move(eqObject("Renoir"), anyInt())).thenReturn(movementResponse)
    }

    private fun boots(): ItemDetails =
        ItemDetails("old_boots", "Old Boots", "", "boots", "", 1, true, true, null, null, null)

    private fun bankedBoots(quantity: Int): BankItemDocument =
        BankItemDocument("old_boots", "Old Boots", "", "boots", "", 1, true, true, null, null, null, quantity)

    private fun characterResponse(character: ArtifactsCharacter): ArtifactsResponseBody<ArtifactsCharacter> {
        @Suppress("UNCHECKED_CAST")
        val response = mock(ArtifactsResponseBody::class.java) as ArtifactsResponseBody<ArtifactsCharacter>
        `when`(response.data).thenReturn(character)
        return response
    }

    private fun depositResponse(character: ArtifactsCharacter): ArtifactsResponseBody<BankItemTransaction> {
        @Suppress("UNCHECKED_CAST")
        val response = mock(ArtifactsResponseBody::class.java) as ArtifactsResponseBody<BankItemTransaction>
        val transaction = mock(BankItemTransaction::class.java)
        `when`(transaction.character).thenReturn(character)
        `when`(response.data).thenReturn(transaction)
        return response
    }

    private fun moveResponse(character: ArtifactsCharacter): ArtifactsResponseBody<MovementResponseBody> {
        @Suppress("UNCHECKED_CAST")
        val response = mock(ArtifactsResponseBody::class.java) as ArtifactsResponseBody<MovementResponseBody>
        val body = mock(MovementResponseBody::class.java)
        `when`(body.character).thenReturn(character)
        `when`(response.data).thenReturn(body)
        return response
    }

    private fun buildCharacter(): ArtifactsCharacter = ArtifactsCharacter(
        name = "Renoir", account = "tellenn", level = 30, gold = 0,
        hp = 100, maxHp = 100, x = 0, y = 0, mapId = 545, layer = "main",
        inventory = emptyArray(), cooldown = 0, skin = null, task = null,
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0,
        criticalStrike = 0, speed = 0, haste = 0, xp = 0, maxXp = 0,
        taskType = null, taskTotal = 0, taskProgress = 0,
        miningXp = 0, miningMaxXp = 0, miningLevel = 0,
        woodcuttingXp = 0, woodcuttingMaxXp = 0, woodcuttingLevel = 0,
        fishingXp = 0, fishingMaxXp = 0, fishingLevel = 0,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 0, weaponcraftingLevel = 0,
        gearcraftingXp = 0, gearcraftingMaxXp = 0, gearcraftingLevel = 0,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 0, jewelrycraftingLevel = 0,
        cookingXp = 0, cookingMaxXp = 0, cookingLevel = 0,
        alchemyXp = 0, alchemyMaxXp = 0, alchemyLevel = 0,
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
}
