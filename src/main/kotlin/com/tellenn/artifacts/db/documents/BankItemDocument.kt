package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.db.documents.ItemEffectDocument.Companion.fromItemEffect
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
    val effects: List<ItemEffectDocument>?,
    val craft: ItemCraftDocument?,
    val conditions: List<ItemConditionDocument>? = null,
    val quantity: Int
) {
    companion object {
        fun fromItemDetails(itemDetails: ItemDetails, quantity: Int): BankItemDocument {
            return BankItemDocument(
                code = itemDetails.code,
                name = itemDetails.name,
                description = itemDetails.description,
                type = itemDetails.type,
                subtype = itemDetails.subtype,
                level = itemDetails.level,
                tradeable = itemDetails.tradeable,
                effects = itemDetails.effects?.map { i -> fromItemEffect(i) }?.toList(),
                craft = itemDetails.craft?.let { ItemCraftDocument.fromItemCraft(it) },
                quantity = quantity
            )
        }

        fun toItemDetails(itemDocument: BankItemDocument): ItemDetails {
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
