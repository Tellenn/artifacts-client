package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class PendingItem(
    @param:JsonProperty("id") val id: String,
    @param:JsonProperty("account") val account: String,
    @param:JsonProperty("source") val source: String,
    @param:JsonProperty("source_id") val sourceId: String?,
    @param:JsonProperty("description") val description: String,
    @param:JsonProperty("gold") val gold: Int?,
    @param:JsonProperty("items") val items: List<SimpleItem>,
    @param:JsonProperty("created_at") val createdAt: String,
    @param:JsonProperty("claimed_at") val claimedAt: String?,
)