package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.models.MapData
import com.tellenn.artifacts.db.documents.MapDocument
import com.tellenn.artifacts.db.repositories.MapRepository
import com.tellenn.artifacts.db.repositories.MonsterRepository
import org.springframework.stereotype.Service

@Service
class MonsterService(private val monsterRepository: MonsterRepository, private val mapRepository: MapRepository) {

    fun findMonsterMap(monsterCode: String): MapData {
        // TODO : Carefull, the cell may be corrupted or event based ?
        val mapDocument = mapRepository.findByContentCode(monsterCode)
        return MapDocument.toMapData(mapDocument)
    }
}