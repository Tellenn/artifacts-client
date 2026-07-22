package com.tellenn.artifacts.services

import com.tellenn.artifacts.AppConfig
import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.exceptions.BankCorruptedException
import com.tellenn.artifacts.exceptions.BattleLostException
import com.tellenn.artifacts.exceptions.CharacterInventoryFullException
import com.tellenn.artifacts.exceptions.MapContentNotFoundException
import com.tellenn.artifacts.exceptions.MissingItemException
import com.tellenn.artifacts.exceptions.NotFoundException
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import kotlin.math.min

/** Role a character plays in a boss fight, driving which potions are selected. */
enum class BossRole { TANK, DPS }

/** The two utility potions to equip for a boss fight (null = leave the slot empty). */
data class BossPotionLoadout(val utility1: String?, val utility2: String?)

/**
 * Service for managing gathering operations.
 * Provides functionality for gathering resources with inventory management.
 */
@Service
class EquipmentService(
    private val bankService: BankService,
    private val monsterService: MonsterService,
    private val itemRepository: ItemRepository,
    private val characterService: CharacterService,
    private val itemService: ItemService,
    private val movementService: MovementService,
    private val battleSimulatorService: BattleSimulatorService,
    private val bankItemSyncService: BankItemSyncService,
    private val accountClient: AccountClient,
    private val merchantService: MerchantService
) {
    fun equipBestAvailableEquipmentForMonsterInBank(character: ArtifactsCharacter, monsterCode: String, threatScoreMult:Int = 1, equipPotions: Boolean = true) : ArtifactsCharacter{
        // Ce chemin arrive souvent avec un snapshot périmé (retour de tâche, thread relancé) :
        // on repart de l'état serveur, sinon moveToBank saute le déplacement (598/490 en banque).
        val freshCharacter = accountClient.getCharacter(character.name).data
        return equipBestEquipmentFromBank(freshCharacter, monsterCode, threatScoreMult, equipPotions)
    }

    private fun equipBestEquipmentFromBank(character: ArtifactsCharacter, monsterCode: String, threatScoreMult: Int, equipPotions: Boolean) : ArtifactsCharacter{
        val bis = findBestEquipmentForMonsterInBank(character, monsterCode, threatScoreMult)
        var newCharacter = movementService.moveToBank(character)
        newCharacter = bankService.emptyInventory(newCharacter)
        val bankWithdraw = ArrayList<SimpleItem>()
        val ring1 = bis["ring1"]
        val ring2 = bis["ring2"]

        if(ring1?.code != ring2?.code){
            if(ring1?.code == character.ring1Slot){
                bis["ring1"] = null
            }
            if(ring2?.code == character.ring2Slot){
                bis["ring2"] = null
            }
            if(ring1?.code != character.ring1Slot && ring1?.code == character.ring2Slot){
                bis["ring1"] = null
            }

            if(ring2?.code != character.ring2Slot && ring2?.code == character.ring1Slot){
                bis["ring2"] = null
            }
        }
        val equippedArtifacts = setOf(character.artifact1Slot, character.artifact2Slot, character.artifact3Slot)
        bis.forEach { slot, item ->
            if(item?.code != null && character[slot+"_slot"] != item.code) {
                val isArtifactSlot = slot in setOf("artifact1", "artifact2", "artifact3")
                if (isArtifactSlot && equippedArtifacts.contains(item.code)) return@forEach
                // Ring specific case
                if(bankWithdraw.contains(SimpleItem(item.code, 1))){
                    bankWithdraw.remove(SimpleItem(item.code, 1))
                    bankWithdraw.add(SimpleItem(item.code, 2))
                }
                bankWithdraw.add(SimpleItem(item.code, 1))
            }
        }
        try {
            newCharacter = bankService.withdrawMany(bankWithdraw, newCharacter)
            bis.forEach { slot,item ->
                if(item?.code != null && character[slot+"_slot"] != item.code) {
                    val isArtifactSlot = slot in setOf("artifact1", "artifact2", "artifact3")
                    if (isArtifactSlot && equippedArtifacts.contains(item.code)) return@forEach
                    newCharacter = characterService.equip(newCharacter, item.code, slot, 1)
                }
            }

        if(character.runeSlot == null && bis["rune_slot"] == null && character.level >= 20){
            var rune = "healing_rune"
            if(character.name == "Cloud"){
                rune = "burn_rune"
            }
            newCharacter = merchantService.buy(rune, newCharacter)
            newCharacter = characterService.equip(newCharacter, rune, "rune_slot", 1)
        }
        }catch (_: NotFoundException){
            newCharacter = accountClient.getCharacter(newCharacter.name).data
            return equipBestAvailableEquipmentForMonsterInBank(newCharacter, monsterCode, threatScoreMult, equipPotions)
        }catch (_: CharacterInventoryFullException){
            newCharacter = accountClient.getCharacter(newCharacter.name).data
            newCharacter = bankService.emptyInventory(newCharacter)
            return equipBestAvailableEquipmentForMonsterInBank(newCharacter, monsterCode, threatScoreMult, equipPotions)
        }catch (_: MapContentNotFoundException){
            // This means we have a desync and the character isn't where we think it is
            var newCharacter = accountClient.getCharacter(newCharacter.name).data
            newCharacter = movementService.moveToBank(newCharacter)
            return equipBestAvailableEquipmentForMonsterInBank(newCharacter, monsterCode, threatScoreMult, equipPotions)
        }catch (_: BankCorruptedException){
            // This means we have a desync and the character isn't where we think it is
            val newCharacter = accountClient.getCharacter(newCharacter.name).data
            return equipBestAvailableEquipmentForMonsterInBank(newCharacter, monsterCode, threatScoreMult, equipPotions)
        }
        newCharacter = movementService.moveToBank(newCharacter)
        newCharacter = bankService.emptyInventory(newCharacter)

        // Potions et nourriture sont un confort : si le cache banque est périmé (404/478),
        // on part combattre sans plutôt que de faire échouer toute la mission.
        try {
            // Fetch healing potions if there are any interesting.
            // Boss fights skip this generic path and apply role-based potions themselves.
            if (equipPotions) {
                newCharacter = equipBestPotionsForFight(newCharacter, monsterCode)
            }

            // Fetch healing outside of combat items if there are any interesting
            val healingItemInBank = itemService.getHealingItems(bankService.getAll())
            if(healingItemInBank.isNotEmpty()){
                val worstHealingItem = healingItemInBank
                    .map { itemService.getItem(it.code) }
                    .filter { it.craft != null && it.level <= newCharacter.level}
                if(worstHealingItem.isNotEmpty()){
                    val worstHealingItemCode = worstHealingItem.minBy { it.level }.code
                    newCharacter = bankService.withdrawOne(worstHealingItemCode, min(newCharacter.inventoryMaxItems /2, bankService.getOne(worstHealingItemCode).quantity), newCharacter)
                }
            }
        }catch (e: NotFoundException){
            logger.warn("Cache banque périmé en récupérant potions/nourriture pour {}, combat sans : {}", newCharacter.name, e.message)
        }catch (e: MissingItemException){
            logger.warn("Cache banque périmé en récupérant potions/nourriture pour {}, combat sans : {}", newCharacter.name, e.message)
        }
        return newCharacter
    }

    fun equipBestPotionsForFight(character: ArtifactsCharacter, monsterCode: String): ArtifactsCharacter {
        if (winsAgainst(monsterCode, character)) {
            return character
        }
        val monster = monsterService.getMonster(monsterCode)
        val monsterEffects = monster.effects.map { it.code }

        // Antidote pour les monstres empoisonnés. L'API de simulation modélise poison + antipoison,
        // on peut donc tester réellement si l'antidote suffit à emporter le combat.
        if (monsterEffects.contains("poison")) {
            val antidote = "small_antidote"
            val withAntidote = character.copy().apply { utility2Slot = antidote }
            if (winsAgainst(monsterCode, withAntidote)) {
                val maxAvailable = bankService.getOne(antidote)
                if (maxAvailable.quantity > 0) {
                    val quantity = min(100, maxAvailable.quantity)
                    val newCharacter = bankService.withdrawOne(antidote, quantity, character)
                    return characterService.equip(newCharacter, antidote, "utility2", quantity)
                }
            }
        }

        // Potion de soin : on retient la plus faible qui emporte le combat.
        var weakestPotion: ItemDetails? = null
        val potions = bankService.getHealingPotions()
            .filter { it.level <= character.level }
            .toMutableList()
        while (potions.isNotEmpty()) {
            weakestPotion = potions.minBy { it.level }
            potions.remove(weakestPotion)
            val withHealing = character.copy().apply { utility1Slot = weakestPotion!!.code }
            if (winsAgainst(monsterCode, withHealing)) {
                var newCharacter = movementService.moveToBank(character)
                val maxAvailable = bankService.getOne(weakestPotion.code)
                val quantity = min(100, maxAvailable.quantity)
                newCharacter = bankService.withdrawOne(weakestPotion.code, quantity, newCharacter)
                return characterService.equip(newCharacter, weakestPotion.code, "utility1", quantity)
            }
        }

        // Boost de dégâts sur l'élément d'attaque dominant du monstre, combiné à la potion de soin testée.
        val attacks = mapOf(
            "fire"   to monster.attackFire,
            "earth"  to monster.attackEarth,
            "water"  to monster.attackWater,
            "air"    to monster.attackAir
        )
        val bestMonsterElement = attacks.maxByOrNull { it.value }?.key ?: "fire"
        val effectPotion = "${bestMonsterElement}_boost_potion"
        val withBoost = character.copy().apply {
            utility2Slot = effectPotion
            weakestPotion?.let { utility1Slot = it.code }
        }

        if (winsAgainst(monsterCode, withBoost)) {
            var newCharacter = movementService.moveToBank(character)
            if (weakestPotion != null) {
                val maxAvailable = bankService.getOne(weakestPotion.code)
                val quantity = min(100, maxAvailable.quantity)
                newCharacter = bankService.withdrawOne(weakestPotion.code, quantity, newCharacter)
                newCharacter = characterService.equip(newCharacter, weakestPotion.code, "utility1", quantity)
            }
            val maxAvailable = bankService.getOne(effectPotion)
            if (maxAvailable.quantity > 0) {
                val quantity = min(100, maxAvailable.quantity)
                newCharacter = bankService.withdrawOne(effectPotion, quantity, newCharacter)
                newCharacter = characterService.equip(newCharacter, effectPotion, "utility2", quantity)
            } else {
                // On gagnerait avec la potion mais on ne l'a pas : autant admettre la défaite tout de suite.
                throw BattleLostException(monsterCode)
            }
            return newCharacter
        }
        return character
    }

    /**
     * Simule le combat contre [monsterCode] via l'API du jeu et juge le combat gagnable si le nombre
     * de défaites reste sous [MAX_SIMULATED_LOSSES] (même seuil que [BattleService.isFightWinnable]).
     */
    private fun winsAgainst(monsterCode: String, character: ArtifactsCharacter): Boolean =
        battleSimulatorService.simulateWithApi(monsterCode, character).data.losses <= MAX_SIMULATED_LOSSES

    /**
     * Selects the two boss-fight potions for [character] against [monster] given its [role].
     * Read-only: inspects the bank but withdraws and mutates nothing.
     *
     * - TANK utility1: best resistance potion on the boss's strongest attack element.
     * - DPS  utility1: best damage potion on the character's strongest weapon attack element.
     * - utility2 (both roles): strongest usable healing potion.
     *
     * Returns `null` for a slot when no suitable potion is available in the bank.
     */
    fun selectBossPotions(character: ArtifactsCharacter, monster: MonsterData, role: BossRole): BossPotionLoadout {
        val usablePotions = bankService.getCombatPotions().filter { it.level <= character.level }
        val utility1 = when (role) {
            BossRole.TANK -> bestPotionForEffect(usablePotions, RES_BOOST_PREFIX + strongestAttackElement(monster))
            BossRole.DPS -> strongestWeaponAttackElement(character)
                ?.let { bestPotionForEffect(usablePotions, DMG_BOOST_PREFIX + it) }
        }
        val utility2 = bestPotionForEffect(usablePotions, RESTORE_EFFECT)
        return BossPotionLoadout(utility1, utility2)
    }

    /** Withdraws and equips the role-based potions returned by [selectBossPotions]. */
    fun equipBossPotions(character: ArtifactsCharacter, monster: MonsterData, role: BossRole): ArtifactsCharacter {
        val loadout = selectBossPotions(character, monster, role)
        var newCharacter = equipPotionInSlot(character, loadout.utility1, "utility1")
        newCharacter = equipPotionInSlot(newCharacter, loadout.utility2, "utility2")
        return newCharacter
    }

    private fun equipPotionInSlot(character: ArtifactsCharacter, potionCode: String?, slot: String): ArtifactsCharacter {
        if (potionCode == null) {
            return character
        }
        val available = bankService.getOne(potionCode).quantity
        val freeSpace = character.inventoryMaxItems - character.inventory.sumOf { it.quantity }
        if (available <= 0 || freeSpace <= 0) {
            return character
        }
        val quantity = min(100, min(available, freeSpace))
        val newCharacter = bankService.withdrawOne(potionCode, quantity, character)
        return characterService.equip(newCharacter, potionCode, slot, quantity)
    }

    /** Highest-value potion carrying [effectCode], or null if none qualifies. */
    private fun bestPotionForEffect(potions: List<ItemDetails>, effectCode: String): String? {
        return potions
            .mapNotNull { potion -> potion.effects?.find { it.code == effectCode }?.let { potion.code to it.value } }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun strongestAttackElement(monster: MonsterData): String {
        return mapOf(
            "fire" to monster.attackFire,
            "earth" to monster.attackEarth,
            "water" to monster.attackWater,
            "air" to monster.attackAir,
        ).maxBy { it.value }.key
    }

    private fun strongestWeaponAttackElement(character: ArtifactsCharacter): String? {
        val weaponCode = character.weaponSlot
        if (weaponCode.isNullOrEmpty()) {
            return null
        }
        val weapon = itemRepository.findByCode(weaponCode)
        val strongest = mapOf(
            "fire" to weaponAttackValue(weapon, "attack_fire"),
            "earth" to weaponAttackValue(weapon, "attack_earth"),
            "water" to weaponAttackValue(weapon, "attack_water"),
            "air" to weaponAttackValue(weapon, "attack_air"),
        ).maxBy { it.value }
        return if (strongest.value > 0) strongest.key else null
    }

    private fun weaponAttackValue(weapon: ItemDetails, effectCode: String): Int =
        weapon.effects?.find { it.code == effectCode }?.value ?: 0

    /**
     * Renvoie une COPIE de [character] portant le meilleur équipement disponible en banque contre
     * [monsterCode], sans aucun retrait ni équipement réel : seuls les codes de slots sont posés, ce
     * qui suffit à l'API de simulation pour recalculer les stats. À utiliser pour évaluer un combat à
     * son plein potentiel (garde-fou de faisabilité, décision de combat d'événement) sans engager le
     * personnage ni toucher la banque.
     */
    fun bestEquippedCopyForSimulation(character: ArtifactsCharacter, monsterCode: String): ArtifactsCharacter {
        val testCharacter = character.copy()
        findBestEquipmentForMonsterInBank(character, monsterCode).forEach { (slot, item) ->
            if (item != null) {
                testCharacter["${slot}_slot"] = item.code
            }
        }
        return testCharacter
    }

    fun findBestEquipmentForMonsterInBank(character: ArtifactsCharacter, monsterCode: String, threatScoreMult:Int = 1) : MutableMap<String, ItemDetails?>{
        val storedEquipment = bankService.getAllEquipmentsUnderLevel(character.level)
        var availableEquipment : MutableList<BankItemDocument> = storedEquipment.toMutableList()
        getEquippedItems(character = character).forEach { availableEquipment = addItemQuantityByOne(availableEquipment, it)}
        val monster = monsterService.getMonster(monsterCode)
        val bis = getHashMapSlot()
        val bestWeapon = getBestScoreForItems(availableEquipment.filter { it.type == "weapon" }, monster, null,
            threatScoreMult)
        for(slot in bis){
            val item : BankItemDocument?
            if(slot.key == "artifact1") {
                item = getBestScoreForItems(
                    availableEquipment
                        .filter { it.type == "artifact" },
                    monster,
                    bestWeapon,
                    threatScoreMult)
            }else if(slot.key == "artifact2"){
                item = getBestScoreForItems(
                    availableEquipment
                        .filter { it.type == "artifact" }
                        .filter { it.code != bis["artifact1"]?.code },
                    monster,
                    bestWeapon,
                    threatScoreMult)
            }else if(slot.key == "artifact3"){
                item = getBestScoreForItems(
                    availableEquipment
                        .filter { it.type == "artifact" }
                        .filter { it.code != bis["artifact1"]?.code }
                        .filter { it.code != bis["artifact2"]?.code },
                    monster,
                    bestWeapon,
                    threatScoreMult)
            }else if(slot.key == "ring1"){
                item = getBestScoreForItems(availableEquipment.filter { it.type == "ring" }, monster, bestWeapon, threatScoreMult)
            }else if(slot.key == "ring2"){
                item = getBestScoreForItems(availableEquipment.filter { it.type == "ring" && it.code != bis["ring1"]?.code }, monster, bestWeapon, threatScoreMult)
            }else{
                item =
                    getBestScoreForItems(availableEquipment.filter { it.type == slot.key }, monster, bestWeapon,
                        threatScoreMult)
            }
            bis[slot.key] = item
            availableEquipment = reduceItemQuantityByOne(availableEquipment, item?.code ?: "")
        }
        bis["weapon"] = bestWeapon
        return bis.mapValues { BankItemDocument.toItemDetails(it.value) }.toMutableMap()

    }

    fun equipBestAvailableEquipmentForCraftingOrGatheringInBank(character: ArtifactsCharacter) : ArtifactsCharacter{
        val bis = getBestWisdomEquipmentInBank(character)
        var newCharacter = character
        val bankWithdraw = ArrayList<SimpleItem>()
        val ring1 = bis["ring1"]
        val ring2 = bis["ring2"]

        if(ring1?.code != ring2?.code){
            if(ring1?.code == character.ring1Slot){
                bis["ring1"] = null
            }
            if(ring2?.code == character.ring2Slot){
                bis["ring2"] = null
            }
            if(ring1?.code != character.ring1Slot && ring1?.code == character.ring2Slot){
                bis["ring1"] = null
            }

            if(ring2?.code != character.ring2Slot && ring2?.code == character.ring1Slot){
                bis["ring2"] = null
            }
        }
        if(bis.any { it.value != null }) {
            newCharacter = movementService.moveToBank(character)

            val equippedArtifacts = setOf(character.artifact1Slot, character.artifact2Slot, character.artifact3Slot)
            bis.forEach { (slot, item) ->
                if (item?.code != null && character[slot + "_slot"] != item.code) {
                    val isArtifactSlot = slot in setOf("artifact1", "artifact2", "artifact3")
                    if (isArtifactSlot && equippedArtifacts.contains(item.code)) return@forEach
                    // Ring specific case
                    if ((slot == "ring1_slot" || slot == "ring2_slot") && bankWithdraw.contains(SimpleItem(item.code, 1))) {
                        bankWithdraw.remove(SimpleItem(item.code, 1))
                        bankWithdraw.add(SimpleItem(item.code, 2))
                    }
                    bankWithdraw.add(SimpleItem(item.code, 1))
                }
            }
            try {
                newCharacter = bankService.withdrawMany(bankWithdraw, newCharacter)
                val equippedArtifacts = setOf(character.artifact1Slot, character.artifact2Slot, character.artifact3Slot)
                bis.forEach { slot, item ->
                    if (item?.code != null && character[slot + "_slot"] != item.code) {
                        val isArtifactSlot = slot in setOf("artifact1", "artifact2", "artifact3")
                        if (isArtifactSlot && equippedArtifacts.contains(item.code)) return@forEach
                        newCharacter = characterService.equip(newCharacter, item.code, slot, 1)
                    }
                }
            } catch (_: NotFoundException) {
                bankItemSyncService.syncAllItems()
                newCharacter = accountClient.getCharacter(newCharacter.name).data
                return equipBestAvailableEquipmentForCraftingOrGatheringInBank(newCharacter)
            }catch (_: MapContentNotFoundException){
                // This means we have a desync and the character isn't where we think it is
                var newCharacter = accountClient.getCharacter(newCharacter.name).data
                newCharacter = movementService.moveToBank(newCharacter)
                return equipBestAvailableEquipmentForCraftingOrGatheringInBank(newCharacter)
            }
        }
        val oldInventory = character.inventory
        val newInventory = newCharacter.inventory
        // Find the difference between them, and use bankService.deposit to store them
        val oldQuantities = oldInventory.filter { it.code.isNotEmpty() }.groupBy { it.code }.mapValues { it.value.sumOf { slot -> slot.quantity } }
        val newQuantities = newInventory.filter { it.code.isNotEmpty() }.groupBy { it.code }.mapValues { it.value.sumOf { slot -> slot.quantity } }

        val itemsToStore = ArrayList<SimpleItem>()
        newQuantities.forEach { (code, quantity) ->
            val oldQuantity = oldQuantities[code] ?: 0
            if (quantity > oldQuantity) {
                itemsToStore.add(SimpleItem(code, quantity - oldQuantity))
            }
        }

        if (itemsToStore.isNotEmpty()) {
            newCharacter = bankService.deposit(newCharacter, itemsToStore)
        }
        return newCharacter
    }

    fun getBestWisdomEquipmentInBank(character: ArtifactsCharacter) : MutableMap<String, ItemDetails?>{
        val storedEquipment = bankService.getAllEquipmentsUnderLevel(character.level)
        var availableEquipment : MutableList<BankItemDocument> = storedEquipment.toMutableList()
        getEquippedItems(character = character).forEach { availableEquipment = addItemQuantityByOne(availableEquipment, it)}
        val bis = getHashMapSlot()
        for(slot in bis){
            val item : BankItemDocument?
            if(slot.key == "artifact1") {
                item = getBestWisdomGear(
                    availableEquipment
                        .filter { it.type == "artifact" })
            }else if(slot.key == "artifact2"){
                item = getBestWisdomGear(
                    availableEquipment
                        .filter { it.type == "artifact" }
                        .filter { it.code != bis["artifact1"]?.code })
            }else if(slot.key == "artifact3"){
                item = getBestWisdomGear(
                    availableEquipment
                        .filter { it.type == "artifact" }
                        .filter { it.code != bis["artifact1"]?.code }
                        .filter { it.code != bis["artifact2"]?.code })
            }else if(slot.key == "ring1"){
                item = getBestWisdomGear(availableEquipment.filter { it.type == "ring" })
            }else if(slot.key == "ring2"){
                item = getBestWisdomGear(availableEquipment.filter { it.type == "ring" && it.code != bis["ring1"]?.code })
            }else{
                item =
                    getBestWisdomGear(availableEquipment.filter { it.type == slot.key })
            }
            bis[slot.key] = item
            availableEquipment = reduceItemQuantityByOne(availableEquipment, item?.code ?: "")
        }
        bis["weapon"] = null
        return bis.mapValues { BankItemDocument.toItemDetails(it.value) }.toMutableMap()

    }

    private fun getBestWisdomGear(items: List<BankItemDocument>) : BankItemDocument? {
        return items
            .map { item -> item to (item.effects?.filter { it.code == "wisdom" }?.sumOf { it.value } ?: 0) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    fun getBestScoreForItems(items: List<BankItemDocument>, monster: MonsterData, weapon: BankItemDocument?, threatScoreMult: Int = 1) : BankItemDocument? {
        if(items.isEmpty()){
            return null
        }

        val attackAir =   weapon?.effects?.filter { it.code == "attack_air"  }?.map { it.value } ?.firstOrNull() ?: 0
        val attackWater = weapon?.effects?.filter { it.code == "attack_water"}?.map { it.value } ?.firstOrNull() ?: 0
        val attackEarth = weapon?.effects?.filter { it.code == "attack_earth"}?.map { it.value } ?.firstOrNull() ?: 0
        val attackFire =  weapon?.effects?.filter { it.code == "attack_fire" }?.map { it.value } ?.firstOrNull() ?: 0
        val itemMap = HashMap<BankItemDocument, Int>()
        for(item in items){
            var score = 1
            var multiplier = 1.0
            if(item.effects != null){
                for (effect in item.effects) {
                    when(effect.code) {
                        "critical_strike" -> multiplier +=  effect.value / 100.0
                        "dmg" ->             multiplier +=  effect.value / 100.0
                        "hp" ->              score +=       effect.value / 10
                        "prospecting" ->     score +=       effect.value / 10
                        "threat" ->          score +=       effect.value * threatScoreMult / 10
                        "wisdom" ->          score +=       effect.value / 100
                        "haste" ->           score +=       effect.value / 100
                        "res_air" ->         score +=      (monster.attackAir   * effect.value /100.0).toInt()
                        "res_water" ->       score +=      (monster.attackWater * effect.value /100.0).toInt()
                        "res_earth" ->       score +=      (monster.attackEarth * effect.value /100.0).toInt()
                        "res_fire" ->        score +=      (monster.attackFire  * effect.value /100.0).toInt()
                        "attack_air" ->      score +=      (effect.value / (1+ monster.defenseAir/100.0)).toInt()
                        "attack_water" ->    score +=      (effect.value / (1+ monster.defenseWater/100.0)).toInt()
                        "attack_earth" ->    score +=      (effect.value / (1+ monster.defenseEarth/100.0)).toInt()
                        "attack_fire" ->     score +=      (effect.value / (1+ monster.defenseFire/100.0)).toInt()
                        "dmg_air" ->         score +=      (attackAir *   (1 + effect.value / 100.0) - attackAir).toInt()
                        "dmg_water" ->       score +=      (attackWater * (1 + effect.value / 100.0) - attackWater).toInt()
                        "dmg_earth" ->       score +=      (attackEarth * (1 + effect.value / 100.0) - attackEarth).toInt()
                        "dmg_fire" ->        score +=      (attackFire *  (1 + effect.value / 100.0) - attackFire).toInt()
                    }
                }
            }
            itemMap[item] = (score * multiplier).toInt()
        }

        return itemMap.maxBy { it.value }.key
    }

    private fun getHashMapSlot() : HashMap<String, BankItemDocument?>{
        val hashMap = HashMap<String, BankItemDocument?>()
        hashMap["rune"]         = null
        hashMap["shield"]       = null
        hashMap["helmet"]       = null
        hashMap["body_armor"]   = null
        hashMap["leg_armor"]    = null
        hashMap["boots"]        = null
        hashMap["ring1"]        = null
        hashMap["ring2"]        = null
        hashMap["amulet"]       = null
        hashMap["artifact1"]    = null
        hashMap["artifact2"]    = null
        hashMap["artifact3"]    = null
        hashMap["bag"]          = null
        return hashMap
    }

    private fun getEquippedItems(character: ArtifactsCharacter) : List<ItemDetails>{
        val equippedItems = mutableListOf<String>()
        character.weaponSlot    ?.let { equippedItems.add(it) }
        character.runeSlot      ?.let { equippedItems.add(it) }
        character.shieldSlot    ?.let { equippedItems.add(it) }
        character.helmetSlot    ?.let { equippedItems.add(it) }
        character.bodyArmorSlot ?.let { equippedItems.add(it) }
        character.legArmorSlot  ?.let { equippedItems.add(it) }
        character.bootsSlot     ?.let { equippedItems.add(it) }
        character.ring1Slot     ?.let { equippedItems.add(it) }
        character.ring2Slot     ?.let { equippedItems.add(it) }
        character.amuletSlot    ?.let { equippedItems.add(it) }
        character.artifact1Slot ?.let { equippedItems.add(it) }
        character.artifact2Slot ?.let { equippedItems.add(it) }
        character.artifact3Slot ?.let { equippedItems.add(it) }
        character.bagSlot       ?.let { equippedItems.add(it) }

        return itemRepository.findByCodeIn(equippedItems)
    }

    private fun reduceItemQuantityByOne(items: MutableList<BankItemDocument>, code: String): MutableList<BankItemDocument> {
        for (item in items) {
            if (item.code == code) {
                if(item.quantity <= 1){
                    items.remove(item)
                }else{
                    item.quantity -= 1
                }
                return items
            }
        }
        return items
    }

    private fun addItemQuantityByOne(items: MutableList<BankItemDocument>, itemToAdd: ItemDetails): MutableList<BankItemDocument> {
        for (item in items) {
            if (item.code == itemToAdd.code) {
                item.quantity += 1
                return items
            }
        }
        items.add(BankItemDocument.fromItemDetails(itemToAdd, 1))
        return items
    }

    fun equipBestToolForSkill(character: ArtifactsCharacter, skillType: String) : ArtifactsCharacter {
        val storedEquipment = bankService.getAllEquipmentsUnderLevel(AppConfig.maxLevel)
        var availableEquipment : MutableList<BankItemDocument> = storedEquipment.toMutableList()
        getEquippedItems(character = character).forEach { availableEquipment = addItemQuantityByOne(availableEquipment, it)}
        val filteredAvailableEquipment = availableEquipment
            .filter {
                        it.subtype == "tool" &&
                        it.level <= character.getLevelOf(skillType) &&
                        it.effects?.any { it.code.equals(skillType) } == true
            }
        var itemCode: Pair<String, Int?>? = null
        if(filteredAvailableEquipment.isNotEmpty()) {
            itemCode = filteredAvailableEquipment
                .map { Pair(it.code, it.effects?.find { it.code.equals(skillType) }?.value) }
                .minBy { it.second ?: 0 }

        }

        if(itemCode != null && itemCode.first != (character.weaponSlot ?: "")){
            var newCharacter = movementService.moveToBank(character)
            newCharacter = bankService.withdrawOne(itemCode.first, 1, newCharacter)
            newCharacter = characterService.equip(newCharacter, itemCode.first, "weapon", 1)
            // we use the character here to get the code of the previous item equipped
            return bankService.deposit(newCharacter, listOf(SimpleItem(character.weaponSlot ?: "", 1)))
        }
        return character
    }

    companion object {
        private val logger = LogManager.getLogger(EquipmentService::class.java)

        /** Threat multiplier applied to the tank so it holds the boss's aggro. */
        const val TANK_THREAT_MULT = 10

        // 10 simulations : au-delà d'une défaite, le loadout de potions est jugé insuffisant
        // (même seuil que BattleService.MAX_SIMULATED_LOSSES et les monster tasks de TaskService).
        private const val MAX_SIMULATED_LOSSES = 1

        private const val RES_BOOST_PREFIX = "boost_res_"
        private const val DMG_BOOST_PREFIX = "boost_dmg_"
        private const val RESTORE_EFFECT = "restore"
    }
}
