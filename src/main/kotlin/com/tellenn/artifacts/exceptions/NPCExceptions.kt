package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when an NPC is not for sale.
 */
class NPCNotForSaleException(message: String = "NPC not for sale") : 
    ArtifactsApiException(ErrorCodes.NPC_NOT_FOR_SALE, message)

/**
 * Exception thrown when an NPC is not for buy.
 */
class NPCNotForBuyException(message: String = "NPC not for buy") : 
    ArtifactsApiException(ErrorCodes.NPC_NOT_FOR_BUY, message)