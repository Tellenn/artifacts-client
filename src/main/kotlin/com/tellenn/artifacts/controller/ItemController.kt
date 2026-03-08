package com.tellenn.artifacts.controller

import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ItemDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ItemController(val itemRepository: ItemRepository) {

    @GetMapping("/items")
    fun getAllItems(): List<ItemDetails> {
        return itemRepository.findAll()
    }
}
