package com.tellenn.artifacts.clients.responses

import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.Destination
import com.tellenn.artifacts.models.ArtifactsCharacter

open class MovementResponseBody(val cooldown : Cooldown, val destination: Destination, val character: ArtifactsCharacter){


}