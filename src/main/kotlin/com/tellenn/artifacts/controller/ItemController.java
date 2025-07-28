package com.tellenn.artifacts.controller;

import com.tellenn.artifacts.db.documents.ItemDocument;
import com.tellenn.artifacts.db.repositories.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ItemController {

    private final ItemRepository itemRepository;

    @Autowired
    public ItemController(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @GetMapping("/items")
    public List<ItemDocument> getAllItems() {
        return itemRepository.findAll();
    }
}
