package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when an email reset token is invalid.
 */
class InvalidEmailResetTokenException(message: String = "Invalid email reset token") : 
    ArtifactsApiException(ErrorCodes.INVALID_EMAIL_RESET_TOKEN, message)

/**
 * Exception thrown when an email reset token has expired.
 */
class ExpiredEmailResetTokenException(message: String = "Expired email reset token") : 
    ArtifactsApiException(ErrorCodes.EXPIRED_EMAIL_RESET_TOKEN, message)

/**
 * Exception thrown when an email reset token has already been used.
 */
class UsedEmailResetTokenException(message: String = "Used email reset token") : 
    ArtifactsApiException(ErrorCodes.USED_EMAIL_RESET_TOKEN, message)