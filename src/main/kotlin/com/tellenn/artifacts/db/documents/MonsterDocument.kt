package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.clients.models.Effect
import com.tellenn.artifacts.clients.models.MonsterData
import com.tellenn.artifacts.clients.models.MonsterDrop
import com.tellenn.artifacts.clients.models.MonsterEffect
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Page
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "monsters")
data class MonsterDocument(
    @Id
    val code: String,
    val name: String,
    val level: Int,
    val hp: Int,
    val attackFire: Int,
    val attackWater: Int,
    val attackEarth: Int,
    val attackAir: Int,
    val defenseFire: Int,
    val defenseWater: Int,
    val defenseEarth: Int,
    val defenseAir: Int,
    val criticalStrike: Int,
    val effects: List<Effect>,
    val minGold: Int,
    val maxGold: Int,
    val drops: List<MonsterDropDocument>?
) {
    companion object {
        fun fromMonsterData(monsterData: MonsterData): MonsterDocument {
            return MonsterDocument(
                code = monsterData.code,
                name = monsterData.name,
                level = monsterData.level,
                hp = monsterData.hp,
                attackFire = monsterData.attackFire,
                attackWater = monsterData.attackWater,
                attackEarth = monsterData.attackEarth,
                attackAir = monsterData.attackAir,
                defenseFire = monsterData.defenseFire,
                defenseWater = monsterData.defenseWater,
                defenseEarth = monsterData.defenseEarth,
                defenseAir = monsterData.defenseAir,
                criticalStrike = monsterData.criticalStrike,
                effects = monsterData.effects,
                minGold = monsterData.minGold,
                maxGold = monsterData.maxGold,
                drops = monsterData.drops?.map { MonsterDropDocument.fromMonsterDrop(it) }
            )
        }

        fun toMonsterData(monsterDocument: MonsterDocument) : MonsterData {
            return MonsterData(
                code = monsterDocument.code,
                name = monsterDocument.name,
                level = monsterDocument.level,
                hp = monsterDocument.hp,
                attackFire = monsterDocument.attackFire,
                attackWater = monsterDocument.attackWater,
                attackEarth = monsterDocument.attackEarth,
                attackAir = monsterDocument.attackAir,
                defenseFire = monsterDocument.defenseFire,
                defenseWater = monsterDocument.defenseWater,
                defenseEarth = monsterDocument.defenseEarth,
                defenseAir = monsterDocument.defenseAir,
                criticalStrike = monsterDocument.criticalStrike,
                effects = monsterDocument.effects,
                minGold = monsterDocument.minGold,
                maxGold = monsterDocument.maxGold,
                drops = monsterDocument.drops?.map { MonsterDropDocument.toMonsterDrop(it) }
            )
        }
    }
}

data class MonsterDropDocument(
    val code: String,
    val minQuantity: Int,
    val maxQuantity: Int,
    val rate: Int
) {
    companion object {
        fun fromMonsterDrop(monsterDrop: MonsterDrop): MonsterDropDocument {
            return MonsterDropDocument(
                code = monsterDrop.code,
                minQuantity = monsterDrop.minQuantity,
                maxQuantity = monsterDrop.maxQuantity,
                rate = monsterDrop.rate
            )
        }

        fun toMonsterDrop(it: MonsterDropDocument): MonsterDrop {
            return MonsterDrop(
                code = it.code,
                minQuantity = it.minQuantity,
                maxQuantity = it.maxQuantity,
                rate = it.rate
            )
        }
    }
}