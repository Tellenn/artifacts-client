package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.clients.models.ItemDetails
import com.tellenn.artifacts.clients.models.ItemCraft
import com.tellenn.artifacts.clients.models.Effect
import com.tellenn.artifacts.clients.models.ItemCondition
import com.tellenn.artifacts.clients.models.RecipeIngredient
import com.tellenn.artifacts.db.documents.ItemEffectDocument.Companion.fromItemEffect
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "items")
data class ItemDocument(
    @Id
    val code: String,
    val name: String,
    val description: String,
    val type: String,
    val subtype: String,
    val level: Int,
    val tradeable: Boolean,
    val effects: List<ItemEffectDocument>?,
    val craft: ItemCraftDocument?,
    val conditions: List<ItemConditionDocument>? = null,
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

        fun toItemDetails(itemDocument: ItemDocument): ItemDetails {
            return ItemDetails(
                code = itemDocument.code,
                name = itemDocument.name,
                description = itemDocument.description,
                type = itemDocument.type,
                subtype = itemDocument.subtype,
                level = itemDocument.level,
                tradeable = itemDocument.tradeable,
                effects = itemDocument.effects?.map { ItemEffectDocument.toEffect(it) }?.toList(),
                craft = itemDocument.craft?.let { ItemCraftDocument.toItemCraft(it) },
                conditions = itemDocument.conditions?.map { ItemConditionDocument.toItemCondition(it) }?.toList()
            )
        }
    }
}

data class ItemEffectDocument(
    val code: String,
    val value: Int,
    val description: String?
) {
    companion object {
        fun fromItemEffect(itemEffect: Effect): ItemEffectDocument {
            return ItemEffectDocument(
                code = itemEffect.code,
                value = itemEffect.value,
                description = itemEffect.description
            )
        }

        fun toEffect(effectDocument: ItemEffectDocument): Effect {
            return Effect(
                code = effectDocument.code,
                value = effectDocument.value,
                description = effectDocument.description
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

        fun toItemCraft(craftDocument: ItemCraftDocument): ItemCraft {
            return ItemCraft(
                skill = craftDocument.skill,
                level = craftDocument.level,
                items = craftDocument.items.map { RecipeIngredientDocument.toRecipeIngredient(it) },
                quantity = craftDocument.quantity
            )
        }
    }
}

data class ItemConditionDocument(
    val code: String,
    val operator: String,
    val value: Int
) {
    companion object {
        fun fromItemCondition(itemCondition: ItemCondition): ItemConditionDocument {
            return ItemConditionDocument(
                code = itemCondition.code,
                operator = itemCondition.operator,
                value = itemCondition.value
            )
        }

        fun toItemCondition(itemConditionDocument: ItemConditionDocument): ItemCondition {
            return ItemCondition(
                code = itemConditionDocument.code,
                operator = itemConditionDocument.operator,
                value = itemConditionDocument.value
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

        fun toRecipeIngredient(ingredientDocument: RecipeIngredientDocument): RecipeIngredient {
            return RecipeIngredient(
                code = ingredientDocument.code,
                quantity = ingredientDocument.quantity
            )
        }
    }
}
