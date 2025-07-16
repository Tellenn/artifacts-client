package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when a character does not have enough HP.
 */
class CharacterNotEnoughHpException(message: String = "Character does not have enough HP") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_NOT_ENOUGH_HP, message)

/**
 * Exception thrown when a character has maximum utilities equipped.
 */
class CharacterMaximumUtilitiesEquippedException(message: String = "Character has maximum utilities equipped") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_MAXIMUM_UTILITES_EQUIPED, message)

/**
 * Exception thrown when an item is already equipped by a character.
 */
class CharacterItemAlreadyEquippedException(message: String = "Item is already equipped") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_ITEM_ALREADY_EQUIPED, message)

/**
 * Exception thrown when a character is locked.
 */
class CharacterLockedException(message: String = "Character is locked") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_LOCKED, message)

/**
 * Exception thrown when a character is not assigned to a task.
 */
class CharacterNotThisTaskException(message: String = "Character is not assigned to this task") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_NOT_THIS_TASK, message)

/**
 * Exception thrown when a character has too many items for a task.
 */
class CharacterTooManyItemsTaskException(message: String = "Character has too many items for this task") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_TOO_MANY_ITEMS_TASK, message)

/**
 * Exception thrown when a character has no task.
 */
class CharacterNoTaskException(message: String = "Character has no task") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_NO_TASK, message)

/**
 * Exception thrown when a character's task is not completed.
 */
class CharacterTaskNotCompletedException(message: String = "Character's task is not completed") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_TASK_NOT_COMPLETED, message)

/**
 * Exception thrown when a character already has a task.
 */
class CharacterAlreadyTaskException(message: String = "Character already has a task") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_ALREADY_TASK, message)

/**
 * Exception thrown when a character is already on a map.
 */
class CharacterAlreadyMapException(message: String = "Character is already on a map") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_ALREADY_MAP, message)

/**
 * Exception thrown when there is an error with a character's equipment slot.
 */
class CharacterSlotEquipmentErrorException(message: String = "Error with character's equipment slot") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_SLOT_EQUIPMENT_ERROR, message)

/**
 * Exception thrown when a character has insufficient gold.
 */
class CharacterGoldInsufficientException(message: String = "Character has insufficient gold") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_GOLD_INSUFFICIENT, message)

/**
 * Exception thrown when a character does not have the required skill level.
 */
class CharacterNotSkillLevelRequiredException(message: String = "Character does not have the required skill level") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_NOT_SKILL_LEVEL_REQUIRED, message)

/**
 * Exception thrown when a character name is already used.
 */
class CharacterNameAlreadyUsedException(message: String = "Character name is already used") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_NAME_ALREADY_USED, message)

/**
 * Exception thrown when the maximum number of characters has been reached.
 */
class MaxCharactersReachedException(message: String = "Maximum number of characters reached") : 
    ArtifactsApiException(ErrorCodes.MAX_CHARACTERS_REACHED, message)

/**
 * Exception thrown when a character condition is not met.
 */
class CharacterConditionNotMetException(message: String = "Character condition not met") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_CONDITION_NOT_MET, message)

/**
 * Exception thrown when a character's inventory is full.
 */
class CharacterInventoryFullException(message: String = "Character's inventory is full") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_INVENTORY_FULL, message)

/**
 * Exception thrown when a character is not found.
 */
class CharacterNotFoundException(message: String = "Character not found") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_NOT_FOUND, message)

/**
 * Exception thrown when a character is in cooldown.
 */
class CharacterInCooldownException(message: String = "Character is in cooldown") : 
    ArtifactsApiException(ErrorCodes.CHARACTER_IN_COOLDOWN, message)