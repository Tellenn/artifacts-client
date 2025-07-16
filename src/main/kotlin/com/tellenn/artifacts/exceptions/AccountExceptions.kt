package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when a token is invalid.
 */
class TokenInvalidException(message: String = "Token invalid") : 
    ArtifactsApiException(ErrorCodes.TOKEN_INVALID, message)

/**
 * Exception thrown when a token has expired.
 */
class TokenExpiredException(message: String = "Token expired") : 
    ArtifactsApiException(ErrorCodes.TOKEN_EXPIRED, message)

/**
 * Exception thrown when a token is missing.
 */
class TokenMissingException(message: String = "Token missing") : 
    ArtifactsApiException(ErrorCodes.TOKEN_MISSING, message)

/**
 * Exception thrown when token generation fails.
 */
class TokenGenerationFailException(message: String = "Token generation failed") : 
    ArtifactsApiException(ErrorCodes.TOKEN_GENERATION_FAIL, message)

/**
 * Exception thrown when a username is already used.
 */
class UsernameAlreadyUsedException(message: String = "Username already used") : 
    ArtifactsApiException(ErrorCodes.USERNAME_ALREADY_USED, message)

/**
 * Exception thrown when an email is already used.
 */
class EmailAlreadyUsedException(message: String = "Email already used") : 
    ArtifactsApiException(ErrorCodes.EMAIL_ALREADY_USED, message)

/**
 * Exception thrown when the new password is the same as the old password.
 */
class SamePasswordException(message: String = "Same password") : 
    ArtifactsApiException(ErrorCodes.SAME_PASSWORD, message)

/**
 * Exception thrown when the current password is invalid.
 */
class CurrentPasswordInvalidException(message: String = "Current password invalid") : 
    ArtifactsApiException(ErrorCodes.CURRENT_PASSWORD_INVALID, message)

/**
 * Exception thrown when an account is not a member.
 */
class AccountNotMemberException(message: String = "Account not a member") : 
    ArtifactsApiException(ErrorCodes.ACCOUNT_NOT_MEMBER, message)

/**
 * Exception thrown when an account does not own a skin.
 */
class AccountSkinNotOwnedException(message: String = "Account does not own this skin") : 
    ArtifactsApiException(ErrorCodes.ACCOUNT_SKIN_NOT_OWNED, message)