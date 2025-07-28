package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.MonsterClient
import com.tellenn.artifacts.db.documents.MonsterDocument
import com.tellenn.artifacts.db.repositories.MonsterRepository
import com.tellenn.artifacts.services.ServerVersionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.Thread.sleep

@Service
class MonsterSyncService(
    private val monsterClient: MonsterClient,
    private val monsterRepository: MonsterRepository,
    private val serverVersionService: ServerVersionService
) {
    private val logger = LoggerFactory.getLogger(MonsterSyncService::class.java)

    /**
     * Empties the monsters collection in MongoDB and fills it with all monsters from the API.
     * Handles pagination to fetch all monsters.
     *
     * @param pageSize The number of monsters to fetch per page (default: 50)
     * @param forceSync Whether to force the sync regardless of server version (default: false)
     * @return The number of monsters synced
     */
    @Transactional
    fun syncAllMonsters(pageSize: Int = 50, forceSync: Boolean = false): Int {
        logger.info("Starting monster sync process")
        println("[DEBUG_LOG] Starting monster sync process")

        // Empty the database
        logger.info("Emptying monsters collection")
        println("[DEBUG_LOG] Emptying monsters collection")
        monsterRepository.deleteAll()

        var currentPage = 1
        var totalPages = 1
        var totalMonstersProcessed = 0

        // Fetch all pages of monsters
        do {
            logger.debug("Fetching monsters page $currentPage of $totalPages")
            println("[DEBUG_LOG] Fetching monsters page $currentPage of $totalPages")

            try {
                val response = monsterClient.getMonsters(
                    page = currentPage,
                    size = pageSize
                )
                val dataPage = response.data
                totalPages = response.pages

                // Convert MonsterData to MonsterDocument and save to MongoDB
                val monsterDocuments = dataPage.map { MonsterDocument.fromMonsterData(it) }
                monsterRepository.saveAll(monsterDocuments)

                totalMonstersProcessed += dataPage.size
                logger.debug("Processed ${dataPage.size} monsters from page $currentPage")
                println("[DEBUG_LOG] Processed ${dataPage.size} monsters from page $currentPage")
                sleep(500)
                currentPage++
            } catch (e: Exception) {
                logger.error("Failed to fetch monsters page $currentPage", e)
                println("[DEBUG_LOG] Failed to fetch monsters page $currentPage: ${e.message}")
                break
            }
        } while (currentPage <= totalPages)

        // Save the server version after successful sync
        serverVersionService.updateServerVersion()
        logger.info("Monster sync completed and server version updated. Total monsters synced: $totalMonstersProcessed")
        println("[DEBUG_LOG] Monster sync completed and server version updated. Total monsters synced: $totalMonstersProcessed")
        return totalMonstersProcessed
    }

    /**
     * Syncs a single monster by ID
     *
     * @param monsterId The ID of the monster to sync
     * @return True if the sync was successful, false otherwise
     */
    @Transactional
    fun syncMonster(monsterId: String): Boolean {
        logger.info("Syncing monster with ID: $monsterId")
        try {
            val response = monsterClient.getMonster(monsterId)
            val monsterData = response.data

            // Convert MonsterData to MonsterDocument and save to MongoDB
            val monsterDocument = MonsterDocument.fromMonsterData(monsterData)
            monsterRepository.save(monsterDocument)

            logger.info("Successfully synced monster with ID: $monsterId")
            return true
        } catch (e: Exception) {
            logger.error("Failed to sync monster with ID: $monsterId", e)
            return false
        }
    }
}