package com.tellenn.artifacts.models

/**
 * Item achetable chez un marchand fixe (hors événement), avec sa devise, son prix unitaire,
 * le solde de cette devise en banque et le nombre d'exemplaires actuellement finançables.
 */
data class MerchantOffer(
    val code: String,
    val npc: String,
    val currency: String,
    val price: Int,
    val currencyInBank: Int,
    val affordable: Int,
)
