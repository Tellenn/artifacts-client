package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class ItemDetails(
    @JsonProperty("code") val code: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("subtype") val subtype: String,
    @JsonProperty("level") val level: Int,
    @JsonProperty("tradeable") val tradeable: Boolean,
    @JsonProperty("craft") val craft: ItemCraft?,
    @JsonProperty("effects") val effects: List<Effect>?,
    @JsonProperty("conditions") val conditions: List<ItemCondition>?
)

class Effect(
    @JsonProperty("code") val code: String,
    @JsonProperty("value") val value: Int,
    @JsonProperty("description") val description: String?
)

class ItemCraft(
    @JsonProperty("skill") val skill: String,
    @JsonProperty("level") val level: Int,
    @JsonProperty("items") val items: List<RecipeIngredient>,
    @JsonProperty("quantity") val quantity: Int
)

class RecipeIngredient(
    @JsonProperty("code") val code: String,
    @JsonProperty("quantity") val quantity: Int
)

class ItemCondition(
    @JsonProperty("code") val code: String,
    @JsonProperty("operator") val operator: String, // eq, ne, gt, lt
    @JsonProperty("value") val value: Int
)