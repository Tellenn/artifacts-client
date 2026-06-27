package com.tellenn.artifacts.utils

import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import org.slf4j.Logger
import java.lang.Thread.sleep

object PaginatedSyncUtils {

    private const val SYNC_PAGE_DELAY_MS = 500L
    const val DEFAULT_PAGE_SIZE = 50

    fun <T> syncAll(
        logger: Logger,
        label: String,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        clearFn: () -> Unit,
        fetchPage: (page: Int, size: Int) -> ArtifactsArrayResponseBody<T>,
        persistFn: (List<T>) -> Unit,
    ): Int {
        logger.info("Starting $label sync")
        clearFn()
        var currentPage = 1
        var totalPages = 1
        var total = 0
        do {
            try {
                val response = fetchPage(currentPage, pageSize)
                totalPages = response.pages
                persistFn(response.data)
                total += response.data.size
                logger.debug("Fetched ${response.data.size} $label (page $currentPage/$totalPages)")
                sleep(SYNC_PAGE_DELAY_MS)
                currentPage++
            } catch (e: Exception) {
                logger.error("Failed to fetch $label page $currentPage", e)
                break
            }
        } while (currentPage <= totalPages)
        logger.info("$label sync completed. Total: $total")
        return total
    }

    fun <T> collectAll(
        logger: Logger,
        label: String,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        fetchPage: (page: Int, size: Int) -> ArtifactsArrayResponseBody<T>,
    ): List<T> {
        val result = mutableListOf<T>()
        var currentPage = 1
        var totalPages = 1
        do {
            try {
                val response = fetchPage(currentPage, pageSize)
                totalPages = response.pages
                result.addAll(response.data)
                logger.debug("Fetched ${response.data.size} $label (page $currentPage/$totalPages)")
                sleep(SYNC_PAGE_DELAY_MS)
                currentPage++
            } catch (e: Exception) {
                logger.error("Failed to fetch $label page $currentPage", e)
                break
            }
        } while (currentPage <= totalPages)
        return result
    }
}
