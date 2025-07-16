package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when there is insufficient gold in the bank.
 */
class BankInsufficientGoldException(message: String = "Insufficient gold in bank") : 
    ArtifactsApiException(ErrorCodes.BANK_INSUFFICIENT_GOLD, message)

/**
 * Exception thrown when a bank transaction is in progress.
 */
class BankTransactionInProgressException(message: String = "Bank transaction in progress") : 
    ArtifactsApiException(ErrorCodes.BANK_TRANSACTION_IN_PROGRESS, message)

/**
 * Exception thrown when the bank is full.
 */
class BankFullException(message: String = "Bank is full") : 
    ArtifactsApiException(ErrorCodes.BANK_FULL, message)