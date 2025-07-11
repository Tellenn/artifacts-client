package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias

@Suppress("unused")
class ItemDetails(
    @JsonAlias("code") val code: String,
    @JsonAlias("name") val name: String,
    @JsonAlias("description") val description: String,
    @JsonAlias("type") val type: String,
    @JsonAlias("subtype") val subtype: String,
    @JsonAlias("level") val level: Int,
    @JsonAlias("tradeable") val tradeable: Boolean,
    @JsonAlias("craft") val craft: ItemCraft?,
    @JsonAlias("effects") val effects: List<Effect>?,
    @JsonAlias("conditions") val conditions: List<ItemCondition>?
)

class Effect(
    @JsonAlias("code") val code: String,
    @JsonAlias("value") val value: Int,
    @JsonAlias("description") val description: String
)

class ItemCraft(
    @JsonAlias("skill") val skill: String,
    @JsonAlias("level") val level: Int,
    @JsonAlias("items") val items: List<RecipeIngredient>,
    @JsonAlias("quantity") val quantity: Int
)

class RecipeIngredient(
    @JsonAlias("code") val code: String,
    @JsonAlias("quantity") val quantity: Int
)

class ItemCondition(
    @JsonAlias("code") val code: String,
    @JsonAlias("operator") val operator: String, // eq, ne, gt, lt
    @JsonAlias("value") val value: Int
)