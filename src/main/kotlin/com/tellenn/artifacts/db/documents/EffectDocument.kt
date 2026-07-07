package com.tellenn.artifacts.db.documents

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "effects")
data class EffectDocument(
    @Id @param:JsonProperty("code") val code: String,
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("subtype") val subtype: String?,
    @param:JsonProperty("description") val description: String?,
)
