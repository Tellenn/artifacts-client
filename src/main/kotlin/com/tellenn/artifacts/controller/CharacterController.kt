package com.tellenn.artifacts.controller

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.CharacterContextService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class CharacterController(
    private val accountClient: AccountClient,
    private val characterContextService: CharacterContextService,
) {

    @GetMapping("/characters")
    fun getAllItems(): List<ArtifactsCharacter> {
        return accountClient.getCharacters().data
    }

    @GetMapping("/characters/objectives")
    fun getObjectives(): Map<String, String> = characterContextService.getAllObjectives()
}
