package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias
import java.util.Date

class InventorySlot(
    @JsonAlias("slot") val slot : Int,
    @JsonAlias("code") val code : String,
    @JsonAlias("quantity") val quantity : Int
) {
}