package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonAlias

class Destination(
    @JsonAlias("name") name : String,
    @JsonAlias("skin") skin : String,
    @JsonAlias("x") x : Int,
    @JsonAlias("y") y : Int,
    @JsonAlias("content") content : MapContent,
) {
}