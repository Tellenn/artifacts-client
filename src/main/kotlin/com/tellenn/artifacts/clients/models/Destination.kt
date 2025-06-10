package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias
import java.util.Date

class Destination(
    @JsonAlias("name") name : String,
    @JsonAlias("skin") skin : String,
    @JsonAlias("x") x : Int,
    @JsonAlias("y") y : Int,
    @JsonAlias("content") content : MapContent,
) {
}