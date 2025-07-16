package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when the payload is invalid.
 */
class InvalidPayloadException(message: String = "Invalid payload") : 
    ArtifactsApiException(ErrorCodes.INVALID_PAYLOAD, message)

/**
 * Exception thrown when too many requests are made.
 */
class TooManyRequestsException(message: String = "Too many requests") : 
    ArtifactsApiException(ErrorCodes.TOO_MANY_REQUESTS, message)

/**
 * Exception thrown when a resource is not found.
 */
class NotFoundException(message: String = "Resource not found") : 
    ArtifactsApiException(ErrorCodes.NOT_FOUND, message)

/**
 * Exception thrown when a fatal error occurs.
 */
class FatalErrorException(message: String = "Fatal error") : 
    ArtifactsApiException(ErrorCodes.FATAL_ERROR, message)