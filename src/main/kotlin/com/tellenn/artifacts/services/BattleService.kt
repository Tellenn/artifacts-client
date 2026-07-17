package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BattleClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.exceptions.BattleLostException
import com.tellenn.artifacts.exceptions.CharacterInventoryFullException
import com.tellenn.artifacts.exceptions.MapNotFoundException
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import com.tellenn.artifacts.utils.TimeUtils
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Service
class BattleService(
    private val characterService: CharacterService,
    private val battleClient: BattleClient,
    private val monsterService: MonsterService,
    private val mapService: MapService,
    private val movementService: MovementService,
    private val accountClient: AccountClient,
    private val equipmentService: EquipmentService,
    private val bankService: BankService,
    private val bossFightService: BossFightService,
    private val battleSimulatorService: BattleSimulatorService,
    private val timeUtils: TimeUtils,
    private val eventService: EventService,
) {

    private val log = LogManager.getLogger(GatheringService::class.java)

    // Verdicts de faisabilité mémoïsés : une même recette (et chaque passe de la boucle crafter) re-teste
    // sans cesse les mêmes monstres. Sans cache, chaque test relance 1-2 `/simulation/fight` et sature la
    // limite « 1 req/s ». Le niveau du personnage fait partie de la clé → un level-up réévalue le combat.
    private data class WinnabilityKey(val characterName: String, val level: Int, val monsterCode: String)
    private data class WinnabilityVerdict(val winnable: Boolean, val computedAt: java.time.Instant)
    private val winnabilityCache = ConcurrentHashMap<WinnabilityKey, WinnabilityVerdict>()

    companion object {
        // 10 simulations : au-delà d'une défaite, le combat est jugé trop risqué
        // (même seuil que les monster tasks de TaskService).
        private const val MAX_SIMULATED_LOSSES = 1

        // Durée de validité d'un verdict de faisabilité. Assez long pour absorber les re-tests d'une même
        // boucle crafter, assez court pour retenter un combat devenu gagnable (nouveau stuff en banque).
        private val WINNABILITY_TTL: Duration = Duration.ofMinutes(5)
    }

    /**
     * Simule le combat contre [monsterCode] avec le meilleur équipement disponible en banque
     * (sans retrait réel), comme le combat effectif l'équipera via
     * [EquipmentService.equipBestAvailableEquipmentForMonsterInBank].
     *
     * Si le stuff seul perd, on re-simule avec les meilleures potions de combat disponibles en
     * banque : le combat réel les équipe (via [EquipmentService.equipBestPotionsForFight]), la
     * faisabilité doit donc les prendre en compte — sinon un crafter renonce à des composants de
     * monstre qu'il pourrait obtenir avec une potion.
     */
    fun isFightWinnable(character: ArtifactsCharacter, monsterCode: String): Boolean {
        val key = WinnabilityKey(character.name, character.level, monsterCode)
        winnabilityCache[key]?.let { cached ->
            if (Duration.between(cached.computedAt, timeUtils.now()) < WINNABILITY_TTL) {
                return cached.winnable
            }
        }
        val winnable = computeFightWinnable(character, monsterCode)
        winnabilityCache[key] = WinnabilityVerdict(winnable, timeUtils.now())
        return winnable
    }

    private fun computeFightWinnable(character: ArtifactsCharacter, monsterCode: String): Boolean {
        val testCharacter = character.copy()
        equipmentService.findBestEquipmentForMonsterInBank(character, monsterCode).forEach { (slot, item) ->
            if (item != null) {
                testCharacter["${slot}_slot"] = item.code
            }
        }
        if (battleSimulatorService.simulateWithApi(monsterCode, testCharacter).data.losses <= MAX_SIMULATED_LOSSES) {
            return true
        }
        val testCharacterWithPotions = withBestBankPotions(testCharacter, monsterCode) ?: return false
        return battleSimulatorService.simulateWithApi(monsterCode, testCharacterWithPotions).data.losses <= MAX_SIMULATED_LOSSES
    }

    /**
     * Renvoie une copie de [testCharacter] portant, dans ses slots utility, les meilleures potions de
     * combat effectivement disponibles en banque : la potion de soin de plus haut niveau utilisable
     * ([utility1Slot]) et le boost de dégâts correspondant à l'élément d'attaque dominant du monstre
     * ([utility2Slot]) — le loadout maximal que [EquipmentService.equipBestPotionsForFight] sait
     * équiper. Lecture seule (aucun retrait). Renvoie `null` si aucune potion pertinente n'est en
     * banque, pour éviter une seconde simulation identique à celle sans potion.
     *
     * L'antidote (monstres empoisonnés) n'est pas modélisé ici : il occupe le même slot que le boost
     * et reste un cas de bord ; la faisabilité y demeure conservatrice, comme avant ce correctif.
     */
    private fun withBestBankPotions(testCharacter: ArtifactsCharacter, monsterCode: String): ArtifactsCharacter? {
        val withPotions = testCharacter.copy()
        var equippedAny = false

        val bestHealingPotion = bankService.getHealingPotions()
            .filter { it.level <= withPotions.level && bankService.getOne(it.code).quantity > 0 }
            .maxByOrNull { it.level }
        if (bestHealingPotion != null) {
            withPotions.utility1Slot = bestHealingPotion.code
            equippedAny = true
        }

        val monster = monsterService.getMonster(monsterCode)
        val dominantAttackElement = mapOf(
            "fire" to monster.attackFire,
            "earth" to monster.attackEarth,
            "water" to monster.attackWater,
            "air" to monster.attackAir,
        ).maxByOrNull { it.value }?.key
        if (dominantAttackElement != null) {
            val boostPotionCode = "${dominantAttackElement}_boost_potion"
            if (bankService.getOne(boostPotionCode).quantity > 0) {
                withPotions.utility2Slot = boostPotionCode
                equippedAny = true
            }
        }

        return if (equippedAny) withPotions else null
    }

    /**
     * Vérifie qu'obtenir [itemCode] par le combat est réaliste pour [character].
     * Les boss sont laissés au chemin boss qui fait sa propre simulation de groupe ;
     * un item sans monstre connu est considéré comme hors combat (non bloquant).
     */
    fun isFightForItemWinnable(character: ArtifactsCharacter, itemCode: String): Boolean {
        val monster = monsterService.findMonsterThatDrop(itemCode) ?: return true
        if (monster.type == "boss") {
            return true
        }
        return isFightWinnable(character, monster.code)
    }

    /**
     * Vérifie que le monstre qui drop [itemCode] est actuellement présent sur une map (API live).
     * Les monstres d'événement n'existent sur la carte que pendant leur événement — le cache
     * local de maps peut prétendre le contraire. Les monstres permanents sont toujours présents,
     * aucune requête live n'est faite pour eux ; un item sans monstre connu est non bloquant.
     */
    fun isMonsterForItemOnMap(itemCode: String): Boolean {
        val monster = monsterService.findMonsterThatDrop(itemCode) ?: return true
        if (!eventService.isEventMonster(monster.code)) {
            return true
        }
        return monsterService.findMonsterMapOrNull(monster.code) != null
    }


    fun battleUntilInvIsFull(character: ArtifactsCharacter, monsterCode: String): ArtifactsCharacter{
        var currentCharacter = character

        log.debug("Character ${character.name} starting to gather resource until inventory full")

        // Continue gathering until inventory is full
        while (!characterService.isInventoryFull(currentCharacter)) {
            try {
                currentCharacter = battle(currentCharacter, monsterCode)
            } catch (e: Exception) {
                log.error("Error while gathering: ${e.message}")
                break
            }
        }

        log.info("Character ${currentCharacter.name} finished fighting, inventory is now full or fighting failed")
        return currentCharacter
    }

    fun fightToGetItem(character: ArtifactsCharacter, itemCode: String, quantity: Int, shouldTrain: Boolean = false): ArtifactsCharacter {
        val monster = monsterService.findMonsterThatDrop(itemCode)
        if(monster == null){
            log.error("Monster with itemcode $itemCode not found")
            return character
        }
        if(monster.type == "boss"){
            return bossFightService.tryFightForItem(monster.code, itemCode, quantity)
        }
        // Un combat perdu d'avance coûte un aller-retour complet + le cooldown de défaite :
        // on simule avant de bouger. Les personnages en mode entraînement assument leurs défaites.
        if (!shouldTrain && !isFightWinnable(character, monster.code)) {
            log.info("{} ne combat pas {} pour {} : la simulation prédit une défaite", character.name, monster.code, itemCode)
            throw BattleLostException(monster.code)
        }
        // Un monstre d'événement n'a de map que pendant son événement et change de position à
        // chaque apparition : résolution via l'API live, qui jette UnknownMapException si
        // l'événement est inactif — au lieu d'envoyer le personnage sur une position périmée
        // du cache où le combat échouerait en 598.
        val map = if (eventService.isEventMonster(monster.code)) {
            mapService.findClosestMapFromApi(character, contentCode = monster.code)
        } else {
            mapService.findClosestMap(character, contentCode = monster.code)
        }
        var newCharacter = equipmentService.equipBestAvailableEquipmentForMonsterInBank(character, monster.code)
        newCharacter = movementService.moveCharacterToCell(map.mapId, newCharacter)
        // Les drops annexes remplissent l'inventaire bien avant la quantité cible : on mémorise
        // ce qui part en banque à chaque débordement pour ne combattre que pour le solde restant.
        var bankedQuantity = 0
        try {
            while (!characterService.has(newCharacter, quantity - bankedQuantity, itemCode)){
                try {
                    newCharacter = battle(newCharacter, monster.code)
                }catch (_ : CharacterInventoryFullException){
                    bankedQuantity += newCharacter.inventory.filter { it.code == itemCode }.sumOf { it.quantity }
                    newCharacter = movementService.moveToBank(newCharacter)
                    newCharacter = bankService.emptyInventory(newCharacter)
                    // emptyInventory dépose la nourriture et déséquipe les potions utilitaires : sans
                    // ré-équipement, le farm repartait sans potion ni nourriture après le premier
                    // débordement. On ré-applique gear + potions conditionnelles + nourriture.
                    newCharacter = equipmentService.equipBestAvailableEquipmentForMonsterInBank(newCharacter, monster.code)
                    newCharacter = movementService.moveCharacterToCell(map.mapId, newCharacter)
                }
            }
        }catch (e : BattleLostException){
            newCharacter = accountClient.getCharacter(newCharacter.name).data
            if(shouldTrain){
                newCharacter = train(newCharacter, -1)
                return fightToGetItem(newCharacter, itemCode, quantity - bankedQuantity, shouldTrain)
            }else{
                throw e
            }
        }
        return newCharacter
    }

    fun train(character: ArtifactsCharacter, penalty: Int) : ArtifactsCharacter{
        var newCharacter = character
        try{
            val monster = monsterService.findStrongestMonsterUnderLevel(character.level + penalty)
            val mapData = mapService.findClosestMap(character, contentCode = monster.code)
            newCharacter = equipmentService.equipBestAvailableEquipmentForMonsterInBank(character, monster.code)

            newCharacter = movementService.moveCharacterToCell(mapData.mapId, newCharacter)

            while (character.level == newCharacter.level){
                newCharacter = battle(newCharacter, monster.code)
            }
        }catch (_ : BattleLostException){
            newCharacter = accountClient.getCharacter(newCharacter.name).data
            newCharacter = train(newCharacter, penalty-1)
        }catch (e : MapNotFoundException){
            newCharacter = accountClient.getCharacter(newCharacter.name).data
            log.warn("Map not found for character ${character.name}", e)
            return train(newCharacter, penalty-1)
        }
        return newCharacter
    }

    fun battle(character: ArtifactsCharacter, monsterCode: String) : ArtifactsCharacter{
        var currentCharacter = character
        val response = battleClient.fight(currentCharacter.name)

        // Update character with the latest data
        currentCharacter = response.data.characters.first()
        log.debug("Character ${currentCharacter.name} gathered resource, inventory: ${characterService.countInventoryItems(currentCharacter)}/${currentCharacter.inventoryMaxItems}")

        if(currentCharacter.hp * 2 < (currentCharacter.maxHp * 1.1)){
            log.debug("Character ${currentCharacter.name} is wounded, resting...")
            // If still wounded or still haven't eaten
            if(currentCharacter.hp < currentCharacter.maxHp ){
                currentCharacter = characterService.rest(currentCharacter)
            }

            if(response.data.fight?.result.equals("loss")){
                // If we ran out of potion during the fight, then we try to fetch some more
                if((character.utility1Slot != "" && currentCharacter.utility1Slot == "" )||
                   (character.utility2Slot != "" && currentCharacter.utility2Slot == "" )){
                    currentCharacter = equipmentService.equipBestPotionsForFight(currentCharacter, monsterCode)
                    return battle(currentCharacter, monsterCode)
                }else{
                    // We lost because the fight is too tough or bad luck
                    throw BattleLostException(monsterCode)
                }
            }
        }

        return currentCharacter
    }
}