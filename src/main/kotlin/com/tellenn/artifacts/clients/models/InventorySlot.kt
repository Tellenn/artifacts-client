package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias
import java.util.Date

class InventorySlot(
    @JsonAlias("slot") name : Int,
    @JsonAlias("code") skin : String,
    @JsonAlias("quantity") x : Int
) {
}