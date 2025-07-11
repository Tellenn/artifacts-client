package com.tellenn.artifacts.clients.responses

import com.tellenn.artifacts.clients.models.Cooldown
import com.tellenn.artifacts.clients.models.Destination
import com.tellenn.artifacts.clients.models.ArtifactsCharacter

open class MovementResponseBody(val cooldown : Cooldown, val destination: Destination, val character: ArtifactsCharacter){


}