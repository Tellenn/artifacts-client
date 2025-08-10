package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@Suppress("unused")
class GEOrderHistory(
    @JsonProperty("id") val id: String,
    @JsonProperty("item_code") val itemCode: String,
    @JsonProperty("item_name") val itemName: String,
    @JsonProperty("quantity") val quantity: Int,
    @JsonProperty("price") val price: Int,
    @JsonProperty("total") val total: Int,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("completed_at") val completedAt: Instant,
    @JsonProperty("status") val status: String
)