package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.clients.models.MapData
import com.tellenn.artifacts.clients.models.MapCell
import com.tellenn.artifacts.clients.models.MapContent
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "maps")
data class MapDocument(
    @Id
    val id: String, // Composite key of x and y coordinates
    val name: String,
    val skin: String,
    val x: Int,
    val y: Int,
    val content: MapContentDocument?
) {
    companion object {
        fun fromMapData(mapData: MapData): MapDocument {
            val content = mapData.content?.let { MapContentDocument.fromMapContent(it) }
            return MapDocument(
                id = "${mapData.x}_${mapData.y}",
                name = mapData.name,
                skin = mapData.skin,
                x = mapData.x,
                y = mapData.y,
                content = content
            )
        }
    }
}

data class MapCellDocument(
    val x: Int = 0,
    val y: Int = 0,
    val type: String,
    val content: MapContentDocument?,
    val characters: List<MapCharacterDocument>? = null
) {
    companion object {
        fun fromMapCell(mapCell: MapCell): MapCellDocument {
            return MapCellDocument(
                type = mapCell.type,
                content = mapCell.content?.let { MapContentDocument.fromMapContent(it) }
            )
        }
    }
}

data class MapContentDocument(
    val type: String,
    val code: String
) {
    companion object {
        fun fromMapContent(mapContent: MapContent): MapContentDocument {
            return MapContentDocument(
                type = mapContent.type,
                code = mapContent.code
            )
        }
    }
}

data class MapCharacterDocument(
    val name: String,
    val level: Int,
    val x: Int,
    val y: Int,
    val hp: Int,
    val maxHp: Int,
    val skin: String?
) {
    companion object {
        fun fromArtifactsCharacter(character: ArtifactsCharacter): MapCharacterDocument {
            return MapCharacterDocument(
                name = character.name,
                level = character.level,
                x = character.x,
                y = character.y,
                hp = character.hp,
                maxHp = character.maxHp,
                skin = character.skin
            )
        }
    }
}
