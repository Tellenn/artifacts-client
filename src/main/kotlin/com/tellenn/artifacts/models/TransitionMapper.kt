package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import lombok.AllArgsConstructor
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "transition_mappers")
@AllArgsConstructor
data class TransitionMapper(
    @Id
    val id: String? = null,
    val sourceMapData: MapData,
    val destinationMapData: MapData,
    val conditions: List<Conditions>? = null
)
