package com.tellenn.artifacts.clients

import com.tellenn.artifacts.config.CharacterConfig
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import okhttp3.Response
import org.springframework.stereotype.Component
import java.io.IOException

/**
 * Records latency and outcome of outgoing requests to the Artifacts API.
 * The measured scope is the HTTP call only — cooldown and rate-limit sleeps are excluded.
 */
@Component
class ClientMetrics(private val meterRegistry: MeterRegistry) {

    companion object {
        private const val TIMER_NAME = "artifacts.api.request"
        private const val IO_ERROR_STATUS = "IO_ERROR"

        /** Collections whose child path segment is an open-ended code — normalized to bound tag cardinality. */
        private val CODE_COLLECTIONS = setOf(
            "items", "monsters", "resources", "npcs", "events",
            "achievements", "tasks", "effects", "badges", "ge",
        )
        private val CHARACTER_NAMES = CharacterConfig.getPredefinedCharacters().map { it.name }.toSet()
    }

    fun executeTimed(client: String, method: String, path: String, call: () -> Response): Response {
        val sample = Timer.start(meterRegistry)
        try {
            val response = call()
            sample.stop(timer(client, method, path, response.code.toString()))
            return response
        } catch (e: IOException) {
            sample.stop(timer(client, method, path, IO_ERROR_STATUS))
            throw e
        }
    }

    private fun timer(client: String, method: String, path: String, status: String): Timer =
        Timer.builder(TIMER_NAME)
            .description("Latency of outgoing HTTP calls to the Artifacts API, excluding cooldown sleeps")
            .tag("client", client)
            .tag("method", method)
            .tag("uri", normalizePath(path))
            .tag("status", status)
            .publishPercentileHistogram()
            .register(meterRegistry)

    private fun normalizePath(path: String): String {
        val segments = path.substringBefore('?').split('/')
        return segments
            .mapIndexed { index, segment ->
                val previous = if (index > 0) segments[index - 1] else ""
                when {
                    segment.isEmpty() -> segment
                    segment in CHARACTER_NAMES -> "{name}"
                    segment.all { it.isDigit() } -> "{id}"
                    previous in CODE_COLLECTIONS -> "{code}"
                    else -> segment
                }
            }
            .joinToString("/")
    }
}
