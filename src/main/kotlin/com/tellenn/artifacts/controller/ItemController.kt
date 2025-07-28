package com.tellenn.artifacts.controller

import com.tellenn.artifacts.db.documents.ItemDocument
import com.tellenn.artifacts.db.repositories.ItemRepository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ItemController(val itemRepository: ItemRepository) {

    @GetMapping("/items")
    fun getAllItems(): List<ItemDocument> {
        return itemRepository.findAll()
    }
}
