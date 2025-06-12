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
    @JsonAlias("tradable") val tradable: Boolean,
    @JsonAlias("slot") val slot: String?,
    @JsonAlias("craft") val craft: ItemCraft?,
    @JsonAlias("effect") val effect: ItemEffect?
)

class ItemEffect(
    @JsonAlias("code") val code: String,
    @JsonAlias("value") val value: Int
)

class ItemCraft(
    @JsonAlias("skill") val skill: String,
    @JsonAlias("level") val level: Int,
    @JsonAlias("ingredients") val ingredients: List<RecipeIngredient>,
    @JsonAlias("quantity") val quantity: Int
)

class RecipeIngredient(
    @JsonAlias("code") val code: String,
    @JsonAlias("quantity") val quantity: Int
)