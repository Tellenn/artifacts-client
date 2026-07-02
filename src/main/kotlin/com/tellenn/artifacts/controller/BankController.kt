package com.tellenn.artifacts.controller

import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.models.BankDetails
import com.tellenn.artifacts.services.BankService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/bank")
class BankController(private val bankService: BankService) {

    @GetMapping("/items")
    fun getItems(): List<BankItemDocument> = bankService.getAllItems()

    @GetMapping("/details")
    fun getDetails(): BankDetails = bankService.getBankDetails()
}
