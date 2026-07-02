package com.tellenn.artifacts.controller

import com.tellenn.artifacts.models.GatheringTaskStatus
import com.tellenn.artifacts.services.GatheringTaskService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class GatheringTaskController(private val gatheringTaskService: GatheringTaskService) {

    @GetMapping("/gathering-tasks")
    fun getQueueStatus(): List<GatheringTaskStatus> = gatheringTaskService.getQueueStatus()
}
