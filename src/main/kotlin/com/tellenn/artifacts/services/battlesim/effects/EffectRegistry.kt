package com.tellenn.artifacts.services.battlesim.effects

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class EffectRegistry(handlers: List<EffectHandler>) {
    private val logger = LoggerFactory.getLogger(EffectRegistry::class.java)
    private val byCode: Map<String, EffectHandler> = handlers.associateBy { it.code }
    private val warned = ConcurrentHashMap.newKeySet<String>()

    fun handlerFor(code: String): EffectHandler? {
        val handler = byCode[code]
        if (handler == null && warned.add(code)) {
            logger.warn("No battle effect handler for '{}' — ignored in local simulation", code)
        }
        return handler
    }
}
