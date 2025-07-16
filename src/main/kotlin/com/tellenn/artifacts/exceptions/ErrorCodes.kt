package com.tellenn.artifacts.exceptions

/**
 * Error codes returned by the Artifacts API.
 */
object ErrorCodes {
    // General
    const val INVALID_PAYLOAD = 422
    const val TOO_MANY_REQUESTS = 429
    const val NOT_FOUND = 404
    const val FATAL_ERROR = 500
    
    // Email token error codes
    const val INVALID_EMAIL_RESET_TOKEN = 560
    const val EXPIRED_EMAIL_RESET_TOKEN = 561
    const val USED_EMAIL_RESET_TOKEN = 562
    
    // Account Error Codes
    const val TOKEN_INVALID = 452
    const val TOKEN_EXPIRED = 453
    const val TOKEN_MISSING = 454
    const val TOKEN_GENERATION_FAIL = 455
    const val USERNAME_ALREADY_USED = 456
    const val EMAIL_ALREADY_USED = 457
    const val SAME_PASSWORD = 458
    const val CURRENT_PASSWORD_INVALID = 459
    const val ACCOUNT_NOT_MEMBER = 451
    const val ACCOUNT_SKIN_NOT_OWNED = 550
    
    // Character Error Codes
    const val CHARACTER_NOT_ENOUGH_HP = 483
    const val CHARACTER_MAXIMUM_UTILITES_EQUIPED = 484
    const val CHARACTER_ITEM_ALREADY_EQUIPED = 485
    const val CHARACTER_LOCKED = 486
    const val CHARACTER_NOT_THIS_TASK = 474
    const val CHARACTER_TOO_MANY_ITEMS_TASK = 475
    const val CHARACTER_NO_TASK = 487
    const val CHARACTER_TASK_NOT_COMPLETED = 488
    const val CHARACTER_ALREADY_TASK = 489
    const val CHARACTER_ALREADY_MAP = 490
    const val CHARACTER_SLOT_EQUIPMENT_ERROR = 491
    const val CHARACTER_GOLD_INSUFFICIENT = 492
    const val CHARACTER_NOT_SKILL_LEVEL_REQUIRED = 493
    const val CHARACTER_NAME_ALREADY_USED = 494
    const val MAX_CHARACTERS_REACHED = 495
    const val CHARACTER_CONDITION_NOT_MET = 496
    const val CHARACTER_INVENTORY_FULL = 497
    const val CHARACTER_NOT_FOUND = 498
    const val CHARACTER_IN_COOLDOWN = 499
    
    // Item Error Codes
    const val ITEM_INSUFFICIENT_QUANTITY = 471
    const val ITEM_INVALID_EQUIPMENT = 472
    const val ITEM_RECYCLING_INVALID_ITEM = 473
    const val ITEM_INVALID_CONSUMABLE = 476
    const val MISSING_ITEM = 478
    
    // Grand Exchange Error Codes
    const val GE_MAX_QUANTITY = 479
    const val GE_NOT_IN_STOCK = 480
    const val GE_NOT_THE_PRICE = 482
    const val GE_TRANSACTION_IN_PROGRESS = 436
    const val GE_NO_ORDERS = 431
    const val GE_MAX_ORDERS = 433
    const val GE_TOO_MANY_ITEMS = 434
    const val GE_SAME_ACCOUNT = 435
    const val GE_INVALID_ITEM = 437
    const val GE_NOT_YOUR_ORDER = 438
    
    // Bank Error Codes
    const val BANK_INSUFFICIENT_GOLD = 460
    const val BANK_TRANSACTION_IN_PROGRESS = 461
    const val BANK_FULL = 462
    
    // Maps Error Codes
    const val MAP_NOT_FOUND = 597
    const val MAP_CONTENT_NOT_FOUND = 598
    
    // NPC Error Codes
    const val NPC_NOT_FOR_SALE = 441
    const val NPC_NOT_FOR_BUY = 442
}