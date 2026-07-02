package com.tellenn.artifacts.clients

import com.tellenn.artifacts.services.ClientErrorService
import com.tellenn.artifacts.services.ws.MessageService
import com.tellenn.artifacts.utils.TimeUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class BaseClientDependencies(
    val clientErrorService: ClientErrorService,
    val messageService: MessageService,
    val timeUtils: TimeUtils,
    val clientMetrics: ClientMetrics,
    @param:Value("\${artifacts.api.url}") val url: String,
    @param:Value("\${artifacts.api.key}") val key: String,
)