package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.MonsterClient
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.exceptions.NotFoundException
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import kotlin.math.min

/**
 * Service for managing gathering operations.
 * Provides functionality for gathering resources with inventory management.
 */
@Service
class EquipmentService(
    private val bankService: BankService,
    private val monsterClient: MonsterClient,
    private val itemRepository: ItemRepository,
    private val characterService: CharacterService,
    private val itemService: ItemService
) {
    private val log = LogManager.getLogger(EquipmentService::class.java)

    fun equipBestAvailableEquipmentForMonsterInBank(character: ArtifactsCharacter, monsterCode: String) : ArtifactsCharacter{
        val bis = findBestEquipmentForMonsterInBank(character, monsterCode)
        var newCharacter = bankService.moveToBank(character)
        val bankWithdraw = ArrayList<SimpleItem>()
        bis.forEach { slot,item ->
            if(item?.code != null && character[slot+"_slot"] != item.code) {
                bankWithdraw.add(SimpleItem(item.code, 1))
            }
        }
        try {
            newCharacter = bankService.withdrawMany(bankWithdraw, newCharacter)
            bis.forEach { slot,item ->
                if(item?.code != null && character[slot+"_slot"] != item.code) {
                    newCharacter = characterService.equip(newCharacter, item.code, slot, 1)
                }
            }
        }catch (_: NotFoundException){
            return equipBestAvailableEquipmentForMonsterInBank(newCharacter, monsterCode)
        }

        newCharacter = bankService.emptyInventory(newCharacter)

        val healingItemInBank = itemService.getHealingItems(bankService.getAll())
        if(healingItemInBank.isNotEmpty()){
            val worstHealingItem = healingItemInBank
                .map { itemService.getItem(it.code) }
                .filter { it.craft != null && it.level <= newCharacter.level}
            if(worstHealingItem.isNotEmpty()){
                val worstHealingItemCode = worstHealingItem.minBy { it.level }.code
                newCharacter = bankService.withdrawOne(worstHealingItemCode, min(newCharacter.inventoryMaxItems -20, bankService.getOne(worstHealingItemCode).quantity), newCharacter)
            }
        }
        return newCharacter
    }

    fun findBestEquipmentForMonsterInBank(character: ArtifactsCharacter, monsterCode: String) : Map<String, ItemDetails?>{
        val storedEquipment = bankService.getAllEquipmentsUnderLevel(character.level)
        var availableEquipment : MutableList<BankItemDocument> = storedEquipment.toMutableList()
        getEquippedItems(character = character).forEach { availableEquipment = addItemQuantityByOne(availableEquipment, it)}
        val monster = monsterClient.getMonster(monsterCode).data
        val bis = getHashMapSlot()
        val bestWeapon = getBestScoreForItems(availableEquipment.filter { it.type == "weapon" }, monster, null)
        for(slot in bis){
            val item : BankItemDocument?
            if(slot.key == "artifacts1") {
                item = getBestScoreForItems(
                    availableEquipment
                        .filter { it.type == "artifacts" },
                    monster,
                    bestWeapon)
            }else if(slot.key == "artifacts2"){
                item = getBestScoreForItems(
                    availableEquipment
                        .filter { it.type == "artifacts" }
                        .filter { it.code != bis["artifacts1"]?.code },
                    monster,
                    bestWeapon)
            }else if(slot.key == "artifacts3"){
                item = getBestScoreForItems(
                    availableEquipment
                        .filter { it.type == "artifacts" }
                        .filter { it.code != bis["artifacts1"]?.code }
                        .filter { it.code != bis["artifacts2"]?.code },
                    monster,
                    bestWeapon)
            }else if(slot.key == "ring1" || slot.key == "ring2"){
                item =
                    getBestScoreForItems(availableEquipment.filter { it.type == "ring" }, monster, bestWeapon)
            }else{
                item =
                    getBestScoreForItems(availableEquipment.filter { it.type == slot.key }, monster, bestWeapon)
            }
            bis[slot.key] = item
            availableEquipment = reduceItemQuantityByOne(availableEquipment, item?.code ?: "")
        }
        bis["weapon"] = bestWeapon
        return bis.mapValues { BankItemDocument.toItemDetails(it.value) }

    }

    fun getBestScoreForItems(items: List<BankItemDocument>, monster: MonsterData, weapon: BankItemDocument?) : BankItemDocument? {
        if(items.isEmpty()){
            return null
        }

        // TODO : The score doesn't work properly, does not equip slime shield or re-equip copper ring (it should equip forest ring at lest), see if it's something about the rounding
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
                        "propecting" ->      score +=       effect.value / 10
                        "haste" ->           score +=       effect.value
                        "res_air" ->         score +=       monster.attackAir   / effect.value
                        "res_water" ->       score +=       monster.attackWater / effect.value
                        "res_earth" ->       score +=       monster.attackEarth / effect.value
                        "res_fire" ->        score +=       monster.attackFire  / effect.value
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

        return itemRepository.findByCodeIn(equippedItems).map { ItemDocument.toItemDetails(it) }
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
        val storedEquipment = bankService.getAllEquipmentsUnderLevel(character.level)
        var availableEquipment : MutableList<BankItemDocument> = storedEquipment.toMutableList()
        getEquippedItems(character = character).forEach { availableEquipment = addItemQuantityByOne(availableEquipment, it)}
        val itemCode = availableEquipment
            .filter {
                        it.subtype == "tool" &&
                        it.level <= character.getLevelOf(skillType) &&
                        it.effects?.any { it.code.equals(skillType) } == true
            }
            .map { Pair(it.code, it.effects?.find { it.code.equals(skillType) }?.value) }
            .maxBy { it.second ?: 0 }
        if(itemCode != null && itemCode.first != (character.weaponSlot ?: "")){
            var newCharacter = bankService.moveToBank(character)
            newCharacter = bankService.withdrawOne(itemCode.first, 1, newCharacter)
            newCharacter = characterService.equip(newCharacter, itemCode.first, "weapon", 1)
            // we use the character here to get the code of the previous item equipped
            return bankService.deposit(newCharacter, listOf(SimpleItem(character.weaponSlot ?: "", 1)))
        }
        return character
    }
}
