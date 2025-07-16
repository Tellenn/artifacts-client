package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when a map is not found.
 */
class MapNotFoundException(message: String = "Map not found") : 
    ArtifactsApiException(ErrorCodes.MAP_NOT_FOUND, message)

/**
 * Exception thrown when map content is not found.
 */
class MapContentNotFoundException(message: String = "Map content not found") : 
    ArtifactsApiException(ErrorCodes.MAP_CONTENT_NOT_FOUND, message)