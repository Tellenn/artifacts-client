package com.tellenn.artifacts.controller

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class CharacterController(private val accountClient: AccountClient) {

    @GetMapping("/characters")
    fun getAllItems(): List<ArtifactsCharacter> {
        return accountClient.getCharacters().data
    }
}
