package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.InventorySlot
import com.tellenn.artifacts.models.MapContent
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.config.MongoTestConfiguration
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Import(MongoTestConfiguration::class)
@Testcontainers
class BankServiceTest {

    @Autowired
    private lateinit var characterService: CharacterService
    private lateinit var bankService: BankService
    private lateinit var bankClient: BankClient
    private lateinit var mapService: MapService
    private lateinit var movementService: MovementService
    private lateinit var bankItemSyncService: BankItemSyncService

    @Autowired
    private lateinit var bankRepository: BankItemRepository

    @Autowired
    private lateinit var itemRepository: ItemRepository

    @BeforeEach
    fun setup() {
        // Create mocks
        bankClient = Mockito.mock(BankClient::class.java)
        mapService = Mockito.mock(MapService::class.java)
        movementService = Mockito.mock(MovementService::class.java)
        bankItemSyncService = Mockito.mock(BankItemSyncService::class.java)

        // Clear repositories
        bankRepository.deleteAll()
        itemRepository.deleteAll()

        // Create the service with real repositories and mocked clients
        bankService = BankService(
            bankClient,
            bankRepository,
            itemRepository,
            mapService,
            movementService,
            characterService,
            bankItemSyncService
        )
    }

    @AfterEach
    fun cleanup() {
        // Clean up after each test
        bankRepository.deleteAll()
        itemRepository.deleteAll()
    }

    @Test
    fun `moveToBank should return original character when already at bank`() {
        // Given
        val bankX = 10
        val bankY = 20
        val characterAtBank = createTestCharacter(arrayOf(), bankX, bankY)

        val bankMapContent = MapContent(type = "building", code = "bank")
        val bankMapData = MapData(name = "Bank", skin = "bank", x = bankX, y = bankY, content = bankMapContent)

        `when`(mapService.findClosestMap(characterAtBank, contentCode = "bank")).thenReturn(bankMapData)

        // When
        val result = bankService.moveToBank(characterAtBank)

        // Then
        assertEquals(characterAtBank, result)
        verify(mapService).findClosestMap(characterAtBank, contentCode = "bank")
        verify(movementService, never()).moveCharacterToCell(bankX, bankY, characterAtBank)
    }

    @Test
    fun `moveToBank should move character to bank when not at bank`() {
        // Given
        val characterX = 5
        val characterY = 5
        val bankX = 10
        val bankY = 20
        val characterNotAtBank = createTestCharacter(arrayOf(), characterX, characterY)
        val characterAfterMove = createTestCharacter(arrayOf(), bankX, bankY)

        val bankMapContent = MapContent(type = "building", code = "bank")
        val bankMapData = MapData(name = "Bank", skin = "bank", x = bankX, y = bankY, content = bankMapContent)

        `when`(mapService.findClosestMap(characterNotAtBank, contentCode = "bank")).thenReturn(bankMapData)
        `when`(movementService.moveCharacterToCell(bankX, bankY, characterNotAtBank)).thenReturn(characterAfterMove)

        // When
        val result = bankService.moveToBank(characterNotAtBank)

        // Then
        assertEquals(characterAfterMove, result)
        verify(mapService).findClosestMap(characterNotAtBank, contentCode = "bank")
        verify(movementService).moveCharacterToCell(bankX, bankY, characterNotAtBank)
    }

    private fun createTestCharacter(inventory: Array<InventorySlot>, x: Int = 0, y: Int = 0): ArtifactsCharacter {
        return ArtifactsCharacter(
            name = "TestCharacter",
            account = "TestAccount",
            level = 1,
            gold = 100,
            hp = 100,
            maxHp = 100,
            x = x,
            y = y,
            inventory = inventory,
            cooldown = 0,
            skin = "default",
            task = null,
            dmg = 10,
            wisdom = 10,
            prospecting = 10,
            criticalStrike = 10,
            speed = 10,
            haste = 10,
            xp = 0,
            maxXp = 100,
            taskType = null,
            taskTotal = 0,
            taskProgress = 0,
            miningXp = 0,
            miningMaxXp = 100,
            miningLevel = 1,
            woodcuttingXp = 0,
            woodcuttingMaxXp = 100,
            woodcuttingLevel = 1,
            fishingXp = 0,
            fishingMaxXp = 100,
            fishingLevel = 1,
            weaponcraftingXp = 0,
            weaponcraftingMaxXp = 100,
            weaponcraftingLevel = 1,
            gearcraftingXp = 0,
            gearcraftingMaxXp = 100,
            gearcraftingLevel = 1,
            jewelrycraftingXp = 0,
            jewelrycraftingMaxXp = 100,
            jewelrycraftingLevel = 1,
            cookingXp = 0,
            cookingMaxXp = 100,
            cookingLevel = 1,
            alchemyXp = 0,
            alchemyMaxXp = 100,
            alchemyLevel = 1,
            inventoryMaxItems = 20,
            attackFire = 0,
            attackEarth = 0,
            attackWater = 0,
            attackAir = 0,
            dmgFire = 0,
            dmgEarth = 0,
            dmgWater = 0,
            dmgAir = 0,
            resFire = 0,
            resEarth = 0,
            resWater = 0,
            resAir = 0,
            weaponSlot = null,
            runeSlot = null,
            shieldSlot = null,
            helmetSlot = null,
            bodyArmorSlot = null,
            legArmorSlot = null,
            bootsSlot = null,
            ring1Slot = null,
            ring2Slot = null,
            amuletSlot = null,
            artifact1Slot = null,
            artifact2Slot = null,
            artifact3Slot = null,
            utility1Slot = "",
            utility1SlotQuantity = 0,
            utility2Slot = "",
            utility2SlotQuantity = 0,
            bagSlot = null,
            cooldownExpiration = null
        )
    }

    private fun createTestItemDocument(code: String, name: String): ItemDocument {
        return ItemDocument(
            code = code,
            name = name,
            description = "Test description",
            type = "Test type",
            subtype = "Test subtype",
            level = 1,
            tradeable = true,
            effects = null,
            craft = null,
            conditions = null
        )
    }

    private fun createTestBankItemDocument(code: String, name: String, quantity: Int): BankItemDocument {
        return BankItemDocument(
            code = code,
            name = name,
            description = "Test description",
            type = "Test type",
            subtype = "Test subtype",
            level = 1,
            tradeable = true,
            effects = null,
            craft = null,
            conditions = null,
            quantity = quantity
        )
    }
}
