package com.tellenn.artifacts.exceptions

/**
 * Base exception class for all Artifacts API exceptions.
 *
 * @param code The error code returned by the API
 * @param message The error message
 */
open class ArtifactsApiException(val code: Int, message: String) : 
    RuntimeException("API Error $code: $message")