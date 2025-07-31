package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.MapClient
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.db.documents.MonsterDocument
import com.tellenn.artifacts.db.repositories.MapRepository
import com.tellenn.artifacts.db.repositories.MonsterRepository
import org.springframework.stereotype.Service

@Service
class MonsterService(
    private val monsterRepository: MonsterRepository,
    private val mapRepository: MapRepository,
    private val mapClient: MapClient
) {

    fun findMonsterMap(monsterCode: String): MapData {
        // Using the client instead of the database because of the dynamic event.
        // TODO : Should we sync the db with event instead ?
        return mapClient.getMaps(content_code = monsterCode).data.first()
    }

    fun findMonsterThatDrop(itemCode: String) : MonsterData?{
        return MonsterDocument.toMonsterData(
            monsterRepository.findFirstByDropsCodeOrderByLevelAsc(itemCode)
        )
    }

    fun findStrongestMonsterUnderLevel(level: Int) : MonsterData{
        return MonsterDocument.toMonsterData(
            monsterRepository.findFirstByLevelLessThanEqualOrderByLevelDesc(level.coerceAtLeast(1))
        )
    }
}