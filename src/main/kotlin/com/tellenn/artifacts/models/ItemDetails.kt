package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class ItemDetails(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("description") val description: String,
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("subtype") val subtype: String,
    @param:JsonProperty("level") val level: Int,
    @param:JsonProperty("tradeable") val tradeable: Boolean,
    @param:JsonProperty("craft") val craft: ItemCraft?,
    @param:JsonProperty("effects") val effects: List<Effect>?,
    @param:JsonProperty("conditions") val conditions: List<ItemCondition>?
)

class Effect(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("value") val value: Int,
    @param:JsonProperty("description") val description: String?
)

class ItemCraft(
    @param:JsonProperty("skill") val skill: String,
    @param:JsonProperty("level") val level: Int,
    @param:JsonProperty("items") val items: List<RecipeIngredient>,
    @param:JsonProperty("quantity") val quantity: Int
)

class RecipeIngredient(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("quantity") val quantity: Int
)

class ItemCondition(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("operator") val operator: String, // eq, ne, gt, lt
    @param:JsonProperty("value") val value: Int
)