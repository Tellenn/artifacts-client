package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.MapClient
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.db.repositories.MonsterRepository
import org.springframework.stereotype.Service

@Service
class MonsterService(
    private val monsterRepository: MonsterRepository,
    private val mapClient: MapClient
) {

    fun findMonsterMap(monsterCode: String): MapData {
        // Using the client instead of the database because of the dynamic event.
        return mapClient.getMaps(content_code = monsterCode).data.first()
    }

    fun findMonsterThatDrop(itemCode: String) : MonsterData?{
        return  monsterRepository.findFirstByDropsCodeOrderByLevelAsc(itemCode)
    }

    fun findStrongestMonsterUnderLevel(level: Int) : MonsterData{
        return monsterRepository.findFirstByLevelLessThanEqualOrderByLevelDesc(level.coerceAtLeast(1))
    }

    fun findMonster(monsterCode: String) : MonsterData{
        return monsterRepository.findByCode(monsterCode)
    }
}