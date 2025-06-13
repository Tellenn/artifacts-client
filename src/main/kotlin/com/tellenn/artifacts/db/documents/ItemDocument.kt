package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.clients.models.ItemDetails
import com.tellenn.artifacts.clients.models.ItemCraft
import com.tellenn.artifacts.clients.models.Effect
import com.tellenn.artifacts.clients.models.RecipeIngredient
import com.tellenn.artifacts.db.documents.ItemEffectDocument.Companion.fromItemEffect
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "items")
data class ItemDocument(
    @Id
    val code: String,
    val name: String,
    val description: String?,
    val type: String,
    val subtype: String,
    val level: Int,
    val tradeable: Boolean,
    val effects: List<ItemEffectDocument>?,
    val craft: ItemCraftDocument?
) {
    companion object {
        fun fromItemDetails(itemDetails: ItemDetails): ItemDocument {
            return ItemDocument(
                code = itemDetails.code,
                name = itemDetails.name,
                description = itemDetails.description,
                type = itemDetails.type,
                subtype = itemDetails.subtype,
                level = itemDetails.level,
                tradeable = itemDetails.tradeable,
                effects = itemDetails.effects?.map { i -> fromItemEffect(i) }?.toList(),
                craft = itemDetails.craft?.let { ItemCraftDocument.fromItemCraft(it) }
            )
        }
    }
}

data class ItemEffectDocument(
    val code: String,
    val value: Int
) {
    companion object {
        fun fromItemEffect(itemEffect: Effect): ItemEffectDocument {
            return ItemEffectDocument(
                code = itemEffect.code,
                value = itemEffect.value
            )
        }
    }
}

data class ItemCraftDocument(
    val skill: String,
    val level: Int,
    val items: List<RecipeIngredientDocument>,
    val quantity: Int
) {
    companion object {
        fun fromItemCraft(itemCraft: ItemCraft): ItemCraftDocument {
            return ItemCraftDocument(
                skill = itemCraft.skill,
                level = itemCraft.level,
                items = itemCraft.items.map { RecipeIngredientDocument.fromRecipeIngredient(it) },
                quantity = itemCraft.quantity
            )
        }
    }
}

data class RecipeIngredientDocument(
    val code: String,
    val quantity: Int
) {
    companion object {
        fun fromRecipeIngredient(recipeIngredient: RecipeIngredient): RecipeIngredientDocument {
            return RecipeIngredientDocument(
                code = recipeIngredient.code,
                quantity = recipeIngredient.quantity
            )
        }
    }
}
