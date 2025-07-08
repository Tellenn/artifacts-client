package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.clients.models.BankDetails
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "banks")
data class BankDocument(
    @Id
    val id: String = "default",
    val maxItems: Int,
    val items: Int,
    val gold: Int
) {
    companion object {
        fun fromBankDetails(bankDetails: BankDetails): BankDocument {
            return BankDocument(
                maxItems = bankDetails.maxItems,
                items = bankDetails.items,
                gold = bankDetails.gold
            )
        }
    }
}
