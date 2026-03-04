package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when an unknown job type is encountered.
 *
 * @param content_type
 * @param content_code
 */
class UnknownMapException(content_type: String?, content_code : String?) :
    RuntimeException("Unknown map for contentType '${content_type ?: "none"}' and contentCode ${content_code ?: "none"}")