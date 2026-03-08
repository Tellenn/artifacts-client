package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.Effect
import com.tellenn.artifacts.models.ItemCondition
import com.tellenn.artifacts.models.ItemCraft
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "bankItems")
data class BankItemDocument(
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
    var quantity: Int
) {
    companion object {
        fun fromItemDetails(itemDetails: ItemDetails?, quantity: Int): BankItemDocument {
            if(itemDetails == null) return BankItemDocument("", "", "", "", "", 0, false, null, null, null, 0)
            return BankItemDocument(
                code = itemDetails.code,
                name = itemDetails.name,
                description = itemDetails.description,
                type = itemDetails.type,
                subtype = itemDetails.subtype,
                level = itemDetails.level,
                tradeable = itemDetails.tradeable,
                effects = itemDetails.effects?.toList(),
                craft = itemDetails.craft,
                quantity = quantity
            )
        }

        fun toItemDetails(itemDocument: BankItemDocument?): ItemDetails? {
            return itemDocument?.let {
                ItemDetails(
                code = itemDocument.code,
                name = itemDocument.name,
                description = itemDocument.description,
                type = itemDocument.type,
                subtype = itemDocument.subtype,
                level = itemDocument.level,
                tradeable = itemDocument.tradeable,
                effects = itemDocument.effects?.toList(),
                craft = itemDocument.craft,
                conditions = itemDocument.conditions?.toList()
                )
            }
        }
    }
}
