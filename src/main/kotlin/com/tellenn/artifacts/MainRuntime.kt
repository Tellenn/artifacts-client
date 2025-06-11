package com.tellenn.artifacts

import com.fasterxml.jackson.databind.ObjectMapper
import com.tellenn.artifacts.clients.ServerStatusClient
import com.tellenn.artifacts.services.ItemSyncService
import com.tellenn.artifacts.utils.TimeSync
import lombok.extern.slf4j.Slf4j
import org.apache.logging.log4j.LogManager
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Slf4j
@Component
class MainRuntime(
    private val serverClient: ServerStatusClient,
    private val objectMapper: ObjectMapper,
    private val timeSync: TimeSync,
    private val itemSyncService: ItemSyncService

) : ApplicationRunner {

    private val log = LogManager.getLogger(MainRuntime::class.java)

    override fun run(args: ApplicationArguments?) {
        // Call the server to get the information
        val serverStatus = serverClient.getServerStatus()
        timeSync.syncWithServerTime(serverStatus.data.serverTime)
        log.info("Time synchronized with server. Offset: ${timeSync.currentOffset}")

        log.info(objectMapper.writeValueAsString(serverStatus.data))
        AppConfig.maxLevel = serverStatus.data.maxLevel

        log.info(objectMapper.writeValueAsString(AppConfig))

        // Synchronize items from the server
        val syncedItemsCount = itemSyncService.syncAllItems()
        log.info("Items synchronized with server. Total items: $syncedItemsCount")
    }
}
