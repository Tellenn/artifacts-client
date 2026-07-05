package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BattleClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.exceptions.BattleLostException
import com.tellenn.artifacts.exceptions.CharacterInventoryFullException
import com.tellenn.artifacts.exceptions.MapNotFoundException
import com.tellenn.artifacts.services.battlesim.BattleSimulatorService
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service

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
    private val battleSimulatorService: BattleSimulatorService
) {

    private val log = LogManager.getLogger(GatheringService::class.java)

    companion object {
        // 10 simulations : au-delà d'une défaite, le combat est jugé trop risqué
        // (même seuil que les monster tasks de TaskService).
        private const val MAX_SIMULATED_LOSSES = 1
    }

    /**
     * Simule le combat contre [monsterCode] avec le meilleur équipement disponible en banque
     * (sans retrait réel), comme le combat effectif l'équipera via
     * [EquipmentService.equipBestAvailableEquipmentForMonsterInBank].
     */
    fun isFightWinnable(character: ArtifactsCharacter, monsterCode: String): Boolean {
        val testCharacter = character.copy()
        equipmentService.findBestEquipmentForMonsterInBank(character, monsterCode).forEach { (slot, item) ->
            if (item != null) {
                testCharacter["${slot}_slot"] = item.code
            }
        }
        return battleSimulatorService.simulateWithApi(monsterCode, testCharacter).data.losses <= MAX_SIMULATED_LOSSES
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
        val map = mapService.findClosestMap(character, contentCode = monster.code)
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