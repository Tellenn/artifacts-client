package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when an unknown job type is encountered.
 *
 * @param jobName The name of the unknown job
 * @param characterName The name of the character with the unknown job
 */
class UnknownJobException(jobName: String, characterName: String) : 
    RuntimeException("Unknown job '$jobName' for character $characterName")