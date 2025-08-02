package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.ItemClient
import com.tellenn.artifacts.clients.MonsterClient
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

/**
 * Service for managing gathering operations.
 * Provides functionality for gathering resources with inventory management.
 */
@Service
class EquipmentService(
    private val gatheringClient: GatheringClient,
    private val itemClient: ItemClient,
    private val mapService: MapService,
    private val movementService: MovementService,
    private val bankService: BankService,
    private val monsterClient: MonsterClient,
    private val itemRepository: ItemRepository,
    private val characterService: CharacterService
) {
    private val log = LogManager.getLogger(EquipmentService::class.java)

    fun equipBestAvailableEquipmentForMonsterInBank(character: ArtifactsCharacter, monsterCode: String) : ArtifactsCharacter{
        val bis = findBestEquipmentForMonsterInBank(character, monsterCode)
        var newCharacter = bankService.moveToBank(character)
        val bankWithdraw = ArrayList<SimpleItem>()
        bis.forEach { slot,item ->
            if(item?.code != null) {
                if(character.get(slot+"_slot") != item.code){
                    bankWithdraw.add(SimpleItem(item.code, 1))
                }
            }
        }
        newCharacter = bankService.withdrawMany(bankWithdraw, newCharacter)
        bis.forEach { slot,item ->
            if(item?.code != null ) {
                if(character.get(slot+"_slot") != item.code){
                    newCharacter = characterService.equip(newCharacter, item.code, slot, 1)
                }
            }
        }
        newCharacter = bankService.emptyInventory(newCharacter)
        return newCharacter
    }

    fun findBestEquipmentForMonsterInBank(character: ArtifactsCharacter, monsterCode: String) : Map<String, ItemDetails?>{
        val storedEquipment = bankService.getAllEquipmentsUnderLevel(character.level)
        var availableEquipment : MutableList<BankItemDocument> = storedEquipment.toMutableList()
        getEquippedItems(character = character).forEach { availableEquipment = addItemQuantityByOne(availableEquipment, it)}
        val monster = monsterClient.getMonster(monsterCode).data
        val bis = getHashMapSlot()
        val bestWeapon = getBestScoreForItems(availableEquipment.filter { it -> it.type == "weapon" }, monster, null)
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
        val attackAir = weapon?.effects?.filter { it.code.equals("attack_air")}?.map { it.value } ?.firstOrNull() ?: 0
        val attackWater = weapon?.effects?.filter { it.code.equals("attack_water")}?.map { it.value } ?.firstOrNull() ?: 0
        val attackEarth = weapon?.effects?.filter { it.code.equals("attack_earth")}?.map { it.value } ?.firstOrNull() ?: 0
        val attackFire = weapon?.effects?.filter { it.code.equals("attack_fire")}?.map { it.value } ?.firstOrNull() ?: 0
        val itemMap = HashMap<BankItemDocument, Int>()
        for(item in items){
            var score = 1
            var multiplier = 1.0
            if(item.effects != null){
                for (effect in item.effects) {
                    when(effect.code) {
                        "attack_air" -> score += effect.value / (1+ monster.defenseAir/100)
                        "attack_water" -> score += effect.value / (1+ monster.defenseWater/100)
                        "attack_earth" -> score += effect.value / (1+ monster.defenseEarth/100)
                        "attack_fire" -> score += effect.value / (1+ monster.defenseFire/100)
                        "critical_strike" -> multiplier += effect.value / 100.0
                        "hp" -> score += effect.value / 10
                        "dmg" -> multiplier += effect.value / 100.0
                        "dmg_air" -> score += attackAir * (1 + effect.value / 100) - attackAir
                        "dmg_water" -> score += attackWater * (1 + effect.value / 100) - attackWater
                        "dmg_earth" -> score += attackEarth * (1 + effect.value / 100) - attackEarth
                        "dmg_fire" -> score += attackFire * (1 + effect.value / 100) - attackFire
                        "propecting" -> score += effect.value / 10
                        "haste" -> score += effect.value
                        "res_air" -> score += effect.value * monster.attackAir / 75
                        "res_water" -> score += effect.value * monster.attackWater / 75
                        "res_earth" -> score += effect.value * monster.attackEarth / 75
                        "res_fire" -> score += effect.value * monster.attackFire / 75
                    }
                }
            }
            itemMap[item] = (score * multiplier).toInt()
        }

        return itemMap.maxBy { it.value }.key
    }

    private fun getHashMapSlot() : HashMap<String, BankItemDocument?>{
        val hashMap = HashMap<String, BankItemDocument?>()
        hashMap["rune"] = null
        hashMap["shield"] = null
        hashMap["helmet"] = null
        hashMap["body_armor"] = null
        hashMap["leg_armor"] = null
        hashMap["boots"] = null
        hashMap["ring1"] = null
        hashMap["ring2"] = null
        hashMap["amulet"] = null
        hashMap["artifact1"] = null
        hashMap["artifact2"] = null
        hashMap["artifact3"] = null
        hashMap["bag"] = null
        return hashMap
    }

    private fun getEquippedItems(character: ArtifactsCharacter) : List<ItemDetails>{
        val equippedItems = mutableListOf<String>()
        character.weaponSlot?.let { equippedItems.add(it) }
        character.shieldSlot?.let { equippedItems.add(it) }
        character.helmetSlot?.let { equippedItems.add(it) }
        character.bodyArmorSlot?.let { equippedItems.add(it) }
        character.legArmorSlot?.let { equippedItems.add(it) }
        character.bootsSlot?.let { equippedItems.add(it) }
        character.ring1Slot?.let { equippedItems.add(it) }
        character.ring2Slot?.let { equippedItems.add(it) }
        character.amuletSlot?.let { equippedItems.add(it) }
        character.artifact1Slot?.let { equippedItems.add(it) }
        character.artifact2Slot?.let { equippedItems.add(it) }
        character.artifact3Slot?.let { equippedItems.add(it) }
        character.bagSlot?.let { equippedItems.add(it) }
        // TODO : Don't use the repo, use the service
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
}
