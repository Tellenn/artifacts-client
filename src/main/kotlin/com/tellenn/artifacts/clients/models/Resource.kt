package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias

/**
 * Represents a resource in the game that can be gathered by characters.
 * Resources are associated with specific skills and have level requirements.
 */
class Resource(
    val name: String,
    val code: String,
    val skill: String,
    val level: Int,
    val drops: List<ResourceDrop>
)

/**
 * Represents an item that can be dropped when gathering a resource.
 * Each drop has a code, drop rate, and quantity range.
 */
class ResourceDrop(
    val code: String,
    val rate: Int,
    @JsonAlias("min_quantity") val minQuantity: Int,
    @JsonAlias("max_quantity") val maxQuantity: Int
)