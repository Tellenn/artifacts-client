package com.tellenn.artifacts.services.sync

import com.tellenn.artifacts.clients.MonsterClient
import com.tellenn.artifacts.db.repositories.MonsterRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.Thread.sleep

@Service
class MonsterSyncService(
    private val monsterClient: MonsterClient,
    private val monsterRepository: MonsterRepository
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

        // Empty the database
        logger.info("Emptying monsters collection")
        monsterRepository.deleteAll()

        var currentPage = 1
        var totalPages = 1
        var totalMonstersProcessed = 0

        // Fetch all pages of monsters
        do {
            logger.debug("Fetching monsters page $currentPage of $totalPages")

            try {
                val response = monsterClient.getMonsters(
                    page = currentPage,
                    size = pageSize
                )
                val dataPage = response.data
                totalPages = response.pages

                monsterRepository.saveAll(dataPage)

                totalMonstersProcessed += dataPage.size
                logger.debug("Processed ${dataPage.size} monsters from page $currentPage")
                sleep(500)
                currentPage++
            } catch (e: Exception) {
                logger.error("Failed to fetch monsters page $currentPage", e)
                break
            }
        } while (currentPage <= totalPages)

        logger.info("Monster sync completed and server version updated. Total monsters synced: $totalMonstersProcessed")
        return totalMonstersProcessed
    }
}