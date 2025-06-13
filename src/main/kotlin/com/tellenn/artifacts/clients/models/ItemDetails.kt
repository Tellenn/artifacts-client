package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias

@Suppress("unused")
class ItemDetails(
    @JsonAlias("code") val code: String,
    @JsonAlias("name") val name: String,
    @JsonAlias("description") val description: String?,
    @JsonAlias("type") val type: String,
    @JsonAlias("subtype") val subtype: String,
    @JsonAlias("level") val level: Int,
    @JsonAlias("tradeable") val tradeable: Boolean,
    @JsonAlias("craft") val craft: ItemCraft?,
    @JsonAlias("effects") val effects: List<Effect>?
)

class Effect(
    @JsonAlias("code") val code: String,
    @JsonAlias("value") val value: Int
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