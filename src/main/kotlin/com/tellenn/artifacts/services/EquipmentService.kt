package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.ItemClient
import com.tellenn.artifacts.clients.MonsterClient
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
        newCharacter = bankService.emptyInventory(character)
        return newCharacter
    }

    fun findBestEquipmentForMonsterInBank(character: ArtifactsCharacter, monsterCode: String) : HashMap<String, ItemDetails?>{
        val storedEquipment = bankService.getAllEquipmentsUnderLevel(character.level)
        val availableEquipment : MutableList<ItemDetails> = storedEquipment.toMutableList()
        availableEquipment.addAll(getEquippedItems(character = character))
        val monster = monsterClient.getMonster(monsterCode).data
        val bis = getHashMapSlot()
        val bestWeapon = getBestScoreForItems(availableEquipment.filter { it -> it.type == "weapon" }, monster, null)
        for(slot in bis){
            if(slot.key == "artifacts2"){
                bis[slot.key] = getBestScoreForItems(
                    availableEquipment
                        .filter { it.type == slot.key }
                        .filter { it.code != bis["artifacts1"]?.code },
                    monster,
                    bestWeapon)
            }else if(slot.key == "artifacts3"){
                bis[slot.key] = getBestScoreForItems(
                    availableEquipment
                        .filter { it.type == slot.key }
                        .filter { it.code != bis["artifacts1"]?.code }
                        .filter { it.code != bis["artifacts2"]?.code },
                    monster,
                    bestWeapon)
            }else {
                bis[slot.key] =
                    getBestScoreForItems(availableEquipment.filter { it.type == slot.key }, monster, bestWeapon)
            }
        }
        bis["weapon"] = bestWeapon
        return bis

    }

    fun getBestScoreForItems(items: List<ItemDetails>, monster: MonsterData, weapon: ItemDetails?) : ItemDetails? {
        if(items.isEmpty()){
            return null
        }
        val attackAir = weapon?.effects?.filter { it.code.equals("attack_air")}?.map { it.value } ?.firstOrNull() ?: 0
        val attackWater = weapon?.effects?.filter { it.code.equals("attack_water")}?.map { it.value } ?.firstOrNull() ?: 0
        val attackEarth = weapon?.effects?.filter { it.code.equals("attack_earth")}?.map { it.value } ?.firstOrNull() ?: 0
        val attackFire = weapon?.effects?.filter { it.code.equals("attack_fire")}?.map { it.value } ?.firstOrNull() ?: 0
        val itemMap = HashMap<ItemDetails, Int>()
        for(item in items){
            var score = 0
            var multiplier = 1
            if(item.effects != null){
                for (effect in item.effects) {
                    when(effect.code) {
                        "attack_air" -> score += effect.value * monster.defenseAir / 100
                        "attack_water" -> score += effect.value * monster.defenseWater / 100
                        "attack_earth" -> score += effect.value * monster.defenseEarth / 100
                        "attack_fire" -> score += effect.value * monster.defenseFire / 100
                        "critical_strike" -> multiplier += effect.value
                        "hp" -> score += effect.value / 10
                        "dmg" -> multiplier += effect.value
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
            itemMap[item] = score * multiplier
        }

        return itemMap.maxBy { it.value }.key
    }

    private fun getHashMapSlot() : HashMap<String, ItemDetails?>{
        val hashMap = HashMap<String, ItemDetails?>()
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
}
