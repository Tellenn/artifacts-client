package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@Suppress("unused")
class GEOrderHistory(
    @param:JsonProperty("id") val id: String,
    @param:JsonProperty("item_code") val itemCode: String,
    @param:JsonProperty("item_name") val itemName: String,
    @param:JsonProperty("quantity") val quantity: Int,
    @param:JsonProperty("price") val price: Int,
    @param:JsonProperty("total") val total: Int,
    @param:JsonProperty("created_at") val createdAt: Instant,
    @param:JsonProperty("completed_at") val completedAt: Instant,
    @param:JsonProperty("status") val status: String
)