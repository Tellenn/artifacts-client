package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

class Destination(
    @JsonProperty("name") name : String,
    @JsonProperty("skin") skin : String,
    @JsonProperty("x") x : Int,
    @JsonProperty("y") y : Int,
    @JsonProperty("content") content : MapContent,
) {
}