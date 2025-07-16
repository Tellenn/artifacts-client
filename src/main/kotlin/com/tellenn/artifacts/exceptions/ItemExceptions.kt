package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when an item has insufficient quantity.
 */
class ItemInsufficientQuantityException(message: String = "Item has insufficient quantity") : 
    ArtifactsApiException(ErrorCodes.ITEM_INSUFFICIENT_QUANTITY, message)

/**
 * Exception thrown when an item is invalid for equipment.
 */
class ItemInvalidEquipmentException(message: String = "Item is invalid for equipment") : 
    ArtifactsApiException(ErrorCodes.ITEM_INVALID_EQUIPMENT, message)

/**
 * Exception thrown when an item is invalid for recycling.
 */
class ItemRecyclingInvalidItemException(message: String = "Item is invalid for recycling") : 
    ArtifactsApiException(ErrorCodes.ITEM_RECYCLING_INVALID_ITEM, message)

/**
 * Exception thrown when an item is invalid for consumption.
 */
class ItemInvalidConsumableException(message: String = "Item is invalid for consumption") : 
    ArtifactsApiException(ErrorCodes.ITEM_INVALID_CONSUMABLE, message)

/**
 * Exception thrown when an item is missing.
 */
class MissingItemException(message: String = "Item is missing") : 
    ArtifactsApiException(ErrorCodes.MISSING_ITEM, message)