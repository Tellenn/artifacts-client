package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.models.Effect
import com.tellenn.artifacts.models.ItemCondition
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "craftedItems")
data class CraftedItemDocument(
    @Id
    val code: String,
    val name: String,
    val description: String,
    val type: String,
    val subtype: String,
    val level: Int,
    val tradeable: Boolean,
    val effects: List<Effect>?,
    val craft: ItemCraft?,
    val conditions: List<ItemCondition>? = null,
    val quantity: Int
) {
    companion object {
        fun fromItemDetails(itemDetails: ItemDetails, quantity: Int): CraftedItemDocument {
            return CraftedItemDocument(
                code = itemDetails.code,
                name = itemDetails.name,
                description = itemDetails.description,
                type = itemDetails.type,
                subtype = itemDetails.subtype,
                level = itemDetails.level,
                tradeable = itemDetails.tradeable,
                effects = itemDetails.effects,
                craft = itemDetails.craft,
                quantity = quantity
            )
        }
    }
}
