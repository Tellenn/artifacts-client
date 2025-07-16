package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when the maximum quantity for a Grand Exchange order is reached.
 */
class GEMaxQuantityException(message: String = "Maximum quantity for Grand Exchange order reached") : 
    ArtifactsApiException(ErrorCodes.GE_MAX_QUANTITY, message)

/**
 * Exception thrown when an item is not in stock in the Grand Exchange.
 */
class GENotInStockException(message: String = "Item not in stock in Grand Exchange") : 
    ArtifactsApiException(ErrorCodes.GE_NOT_IN_STOCK, message)

/**
 * Exception thrown when the price of an item in the Grand Exchange does not match.
 */
class GENotThePriceException(message: String = "Price does not match in Grand Exchange") : 
    ArtifactsApiException(ErrorCodes.GE_NOT_THE_PRICE, message)

/**
 * Exception thrown when a transaction is in progress in the Grand Exchange.
 */
class GETransactionInProgressException(message: String = "Transaction in progress in Grand Exchange") : 
    ArtifactsApiException(ErrorCodes.GE_TRANSACTION_IN_PROGRESS, message)

/**
 * Exception thrown when there are no orders in the Grand Exchange.
 */
class GENoOrdersException(message: String = "No orders in Grand Exchange") : 
    ArtifactsApiException(ErrorCodes.GE_NO_ORDERS, message)

/**
 * Exception thrown when the maximum number of orders in the Grand Exchange is reached.
 */
class GEMaxOrdersException(message: String = "Maximum number of orders in Grand Exchange reached") : 
    ArtifactsApiException(ErrorCodes.GE_MAX_ORDERS, message)

/**
 * Exception thrown when there are too many items in a Grand Exchange order.
 */
class GETooManyItemsException(message: String = "Too many items in Grand Exchange order") : 
    ArtifactsApiException(ErrorCodes.GE_TOO_MANY_ITEMS, message)

/**
 * Exception thrown when an attempt is made to trade with the same account in the Grand Exchange.
 */
class GESameAccountException(message: String = "Cannot trade with same account in Grand Exchange") : 
    ArtifactsApiException(ErrorCodes.GE_SAME_ACCOUNT, message)

/**
 * Exception thrown when an invalid item is used in the Grand Exchange.
 */
class GEInvalidItemException(message: String = "Invalid item in Grand Exchange") : 
    ArtifactsApiException(ErrorCodes.GE_INVALID_ITEM, message)

/**
 * Exception thrown when an attempt is made to modify an order that does not belong to the user in the Grand Exchange.
 */
class GENotYourOrderException(message: String = "Not your order in Grand Exchange") : 
    ArtifactsApiException(ErrorCodes.GE_NOT_YOUR_ORDER, message)