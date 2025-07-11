package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when an unknown job type is encountered.
 *
 * @param jobName The name of the unknown job
 * @param characterName The name of the character with the unknown job
 */
class UnknownMapException(content_type: String?, content_code : String?) :
    RuntimeException("Unknown map for contentType '$content_type' and contentCode ${content_code}")