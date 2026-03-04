package com.tellenn.artifacts.db.repositories

import com.tellenn.artifacts.models.MapData
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MapRepository : MongoRepository<MapData, String> {
    // Find by x and y coordinates
    fun findByXAndY(x: Int, y: Int, pageable: Pageable): Page<MapData>

    // Find by x coordinate
    fun findByX(x: Int, pageable: Pageable): Page<MapData>

    // Find by y coordinate
    fun findByY(y: Int, pageable: Pageable): Page<MapData>

    // Find maps that contain a specific cell type
    @Query("{ 'cells.type': ?0 }")
    fun findByCellType(cellType: String, pageable: Pageable): Page<MapData>

    // Find maps that contain a specific content type
    @Query("{ 'cells.content.type': ?0 }")
    fun findByCellContentType(contentType: String, pageable: Pageable): Page<MapData>

    // Custom query to find maps with multiple criteria
    @Query("{}")
    fun findByDynamicQuery(pageable: Pageable): Page<MapData>

    fun findByInteractionsContentCode(monsterCode: String): MapData
}