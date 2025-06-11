package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias

@Suppress("unused")
class ItemDetails(
    @JsonAlias("code") val code: String,
    @JsonAlias("name") val name: String,
    @JsonAlias("description") val description: String?,
    @JsonAlias("type") val type: String,
    @JsonAlias("rarity") val rarity: String,
    @JsonAlias("level") val level: Int,
    @JsonAlias("value") val value: Int,
    @JsonAlias("weight") val weight: Int,
    @JsonAlias("stackable") val stackable: Boolean,
    @JsonAlias("usable") val usable: Boolean,
    @JsonAlias("equippable") val equippable: Boolean,
    @JsonAlias("slot") val slot: String?,
    @JsonAlias("stats") val stats: ItemStats?,
    @JsonAlias("recipe") val recipe: ItemRecipe?
)

class ItemStats(
    @JsonAlias("hp") val hp: Int?,
    @JsonAlias("attack_fire") val attackFire: Int?,
    @JsonAlias("attack_earth") val attackEarth: Int?,
    @JsonAlias("attack_water") val attackWater: Int?,
    @JsonAlias("attack_air") val attackAir: Int?,
    @JsonAlias("dmg_fire") val dmgFire: Int?,
    @JsonAlias("dmg_earth") val dmgEarth: Int?,
    @JsonAlias("dmg_water") val dmgWater: Int?,
    @JsonAlias("dmg_air") val dmgAir: Int?,
    @JsonAlias("res_fire") val resFire: Int?,
    @JsonAlias("res_earth") val resEarth: Int?,
    @JsonAlias("res_water") val resWater: Int?,
    @JsonAlias("res_air") val resAir: Int?
)

class ItemRecipe(
    @JsonAlias("skill") val skill: String,
    @JsonAlias("level") val level: Int,
    @JsonAlias("ingredients") val ingredients: List<RecipeIngredient>
)

class RecipeIngredient(
    @JsonAlias("code") val code: String,
    @JsonAlias("name") val name: String,
    @JsonAlias("quantity") val quantity: Int
)