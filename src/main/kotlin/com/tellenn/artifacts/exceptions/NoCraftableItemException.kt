package com.tellenn.artifacts.exceptions

/**
 * Exception thrown when no craftable item can be found for a given skill at the
 * character's current level (e.g. while looking for an item to level up a skill).
 *
 * @param skill The crafting/gathering skill being leveled
 * @param level The character's current level in that skill
 */
class NoCraftableItemException(skill: String, level: Int) :
    RuntimeException("No craftable item found for skill '$skill' at level $level")
