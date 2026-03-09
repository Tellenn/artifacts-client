package com.tellenn.artifacts.exceptions

import com.tellenn.artifacts.models.MapData

/**
 * Exception thrown when a map that you can't reach because of conditions
 *
 * @param map The name of the unknown job
 * @param characterName The name of the character with the unknown job
 */
class UnreachableMapException(map: MapData, characterName: String) :
    RuntimeException("Character ${characterName} tried to go to (${map.x},${map.y}) in the ${map.layer} but couldn't")
