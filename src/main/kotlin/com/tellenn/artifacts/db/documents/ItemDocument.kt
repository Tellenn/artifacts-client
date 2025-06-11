package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.clients.models.ItemDetails
import com.tellenn.artifacts.clients.models.ItemRecipe
import com.tellenn.artifacts.clients.models.ItemStats
import com.tellenn.artifacts.clients.models.RecipeIngredient
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "items")
data class ItemDocument(
    @Id
    val code: String,
    val name: String,
    val description: String?,
    val type: String,
    val rarity: String,
    val level: Int,
    val value: Int,
    val weight: Int,
    val stackable: Boolean,
    val usable: Boolean,
    val equippable: Boolean,
    val slot: String?,
    val stats: ItemStatsDocument?,
    val recipe: ItemRecipeDocument?
) {
    companion object {
        fun fromItemDetails(itemDetails: ItemDetails): ItemDocument {
            return ItemDocument(
                code = itemDetails.code,
                name = itemDetails.name,
                description = itemDetails.description,
                type = itemDetails.type,
                rarity = itemDetails.rarity,
                level = itemDetails.level,
                value = itemDetails.value,
                weight = itemDetails.weight,
                stackable = itemDetails.stackable,
                usable = itemDetails.usable,
                equippable = itemDetails.equippable,
                slot = itemDetails.slot,
                stats = itemDetails.stats?.let { ItemStatsDocument.fromItemStats(it) },
                recipe = itemDetails.recipe?.let { ItemRecipeDocument.fromItemRecipe(it) }
            )
        }
    }
}

data class ItemStatsDocument(
    val hp: Int?,
    val attackFire: Int?,
    val attackEarth: Int?,
    val attackWater: Int?,
    val attackAir: Int?,
    val dmgFire: Int?,
    val dmgEarth: Int?,
    val dmgWater: Int?,
    val dmgAir: Int?,
    val resFire: Int?,
    val resEarth: Int?,
    val resWater: Int?,
    val resAir: Int?
) {
    companion object {
        fun fromItemStats(itemStats: ItemStats): ItemStatsDocument {
            return ItemStatsDocument(
                hp = itemStats.hp,
                attackFire = itemStats.attackFire,
                attackEarth = itemStats.attackEarth,
                attackWater = itemStats.attackWater,
                attackAir = itemStats.attackAir,
                dmgFire = itemStats.dmgFire,
                dmgEarth = itemStats.dmgEarth,
                dmgWater = itemStats.dmgWater,
                dmgAir = itemStats.dmgAir,
                resFire = itemStats.resFire,
                resEarth = itemStats.resEarth,
                resWater = itemStats.resWater,
                resAir = itemStats.resAir
            )
        }
    }
}

data class ItemRecipeDocument(
    val skill: String,
    val level: Int,
    val ingredients: List<RecipeIngredientDocument>
) {
    companion object {
        fun fromItemRecipe(itemRecipe: ItemRecipe): ItemRecipeDocument {
            return ItemRecipeDocument(
                skill = itemRecipe.skill,
                level = itemRecipe.level,
                ingredients = itemRecipe.ingredients.map { RecipeIngredientDocument.fromRecipeIngredient(it) }
            )
        }
    }
}

data class RecipeIngredientDocument(
    val code: String,
    val name: String,
    val quantity: Int
) {
    companion object {
        fun fromRecipeIngredient(recipeIngredient: RecipeIngredient): RecipeIngredientDocument {
            return RecipeIngredientDocument(
                code = recipeIngredient.code,
                name = recipeIngredient.name,
                quantity = recipeIngredient.quantity
            )
        }
    }
}