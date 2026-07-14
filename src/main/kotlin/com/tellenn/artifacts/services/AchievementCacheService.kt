package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.models.Achievement
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache TTL des achievements complétés du compte.
 *
 * Le quota API est partagé par tous les personnages (2000 req/h) : interroger
 * `/accounts/{name}/achievements` à chaque recherche de banque, de potion ou de
 * transition l'épuisait. Les achievements changent rarement — un cache de
 * quelques minutes suffit à tous les appelants.
 */
@Service
class AchievementCacheService(
    private val accountClient: AccountClient,
    private val clock: Clock = Clock.systemUTC(),
) {
    private data class CacheEntry(val fetchedAt: Instant, val achievements: List<Achievement>)

    private val cacheByAccount = ConcurrentHashMap<String, CacheEntry>()

    fun getCompletedAchievements(account: String): List<Achievement> {
        val now = clock.instant()
        cacheByAccount[account]
            ?.takeIf { Duration.between(it.fetchedAt, now) < CACHE_TTL }
            ?.let { return it.achievements }

        val achievements = fetchAllCompletedAchievements(account)
        cacheByAccount[account] = CacheEntry(now, achievements)
        return achievements
    }

    fun isUnlocked(account: String, achievementCode: String): Boolean =
        getCompletedAchievements(account).any { it.code == achievementCode }

    private fun fetchAllCompletedAchievements(account: String): List<Achievement> = buildList {
        var page = 1
        do {
            val response = accountClient.getAccountAchievements(account, true, page)
            addAll(response.data)
            page++
        } while (page <= response.pages)
    }

    companion object {
        private val CACHE_TTL: Duration = Duration.ofMinutes(5)
    }
}
