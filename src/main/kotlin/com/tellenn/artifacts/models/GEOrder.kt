package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonAlias
import java.time.Instant

@Suppress("unused")
class GEOrder(
    @JsonAlias("id") val id: String,
    @JsonAlias("item_code") val itemCode: String,
    @JsonAlias("item_name") val itemName: String,
    @JsonAlias("quantity") val quantity: Int,
    @JsonAlias("price") val price: Int,
    @JsonAlias("total") val total: Int,
    @JsonAlias("created_at") val createdAt: Instant,
    @JsonAlias("status") val status: String
)