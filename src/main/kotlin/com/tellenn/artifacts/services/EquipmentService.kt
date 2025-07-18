package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.ItemClient
import com.tellenn.artifacts.clients.MonsterClient
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.ItemDetails
import com.tellenn.artifacts.clients.models.MonsterData
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.GatheringResponseBody
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
    private val mapProximityService: MapProximityService,
    private val movementService: MovementService,
    private val bankService: BankService,
    private val monsterClient: MonsterClient
) {
    private val log = LogManager.getLogger(EquipmentService::class.java)

    fun findBestEquipmentForMonsterInBank(character: ArtifactsCharacter, monsterCode: String) {
        var availableEquipment = bankService.getAllEquipmentsUnderLevel(character.level)
        // TODO : Add user equipment in the mix
        var monster = monsterClient.getMonster(monsterCode).data
        val bis = getHashMapSlot()
        //TODO : DO weapon first, so that the rest of the equipment scaled
        for(slot in bis){
            // TODO filter per equipmentSlot and do getBestScoreForItems
        }
    }

    fun getBestScoreForItems(items: List<ItemDetails>, monster: MonsterData, weapon: ItemDetails?) : ItemDetails {
        if(items.isEmpty()){
            throw IllegalArgumentException("Items list is empty")
        }

        val attackAir = weapon?.effects?.filter { e -> e.code.equals("attack_air")}?.map { e -> e.value } ?.firstOrNull() ?: 0
        val attackWater = weapon?.effects?.filter { e -> e.code.equals("attack_water")}?.map { e -> e.value } ?.firstOrNull() ?: 0
        val attackEarth = weapon?.effects?.filter { e -> e.code.equals("attack_earth")}?.map { e -> e.value } ?.firstOrNull() ?: 0
        val attackFire = weapon?.effects?.filter { e -> e.code.equals("attack_fire")}?.map { e -> e.value } ?.firstOrNull() ?: 0
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
                        "res_air" -> score += effect.value * monster.attackAir / 100
                        "res_water" -> score += effect.value * monster.attackWater / 100
                        "res_earth" -> score += effect.value * monster.attackEarth / 100
                        "res_fire" -> score += effect.value * monster.attackFire / 100
                    }
                }
            }
            itemMap[item] = score * multiplier
        }

        return itemMap.maxBy { it.value }.key
    }

    fun getHashMapSlot() : HashMap<String, ItemDetails?>{

        val hashMap = HashMap<String, ItemDetails?>()
        hashMap["rune_slot"] = null
        hashMap["shield_slot"] = null
        hashMap["helmet_slot"] = null
        hashMap["body_armor_slot"] = null
        hashMap["leg_armor_slot"] = null
        hashMap["boots_slot"] = null
        hashMap["ring1_slot"] = null
        hashMap["ring2_slot"] = null
        hashMap["amulet_slot"] = null
        hashMap["artifact1_slot"] = null
        hashMap["artifact2_slot"] = null
        hashMap["artifact3_slot"] = null
        hashMap["bag_slot"] = null
        return hashMap
    }
}
