package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.clients.models.MonsterData
import com.tellenn.artifacts.clients.models.MonsterDrop
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "monsters")
data class MonsterDocument(
    @Id
    val id: String,
    val name: String,
    val level: Int,
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val xpReward: Int,
    val goldReward: Int,
    val drops: List<MonsterDropDocument>?
) {
    companion object {
        fun fromMonsterData(monsterData: MonsterData): MonsterDocument {
            return MonsterDocument(
                id = monsterData.id,
                name = monsterData.name,
                level = monsterData.level,
                hp = monsterData.hp,
                attack = monsterData.attack,
                defense = monsterData.defense,
                speed = monsterData.speed,
                xpReward = monsterData.xpReward,
                goldReward = monsterData.goldReward,
                drops = monsterData.drops?.map { MonsterDropDocument.fromMonsterDrop(it) }
            )
        }
    }
}

data class MonsterDropDocument(
    val itemId: String,
    val chance: Double
) {
    companion object {
        fun fromMonsterDrop(monsterDrop: MonsterDrop): MonsterDropDocument {
            return MonsterDropDocument(
                itemId = monsterDrop.itemId,
                chance = monsterDrop.chance
            )
        }
    }
}