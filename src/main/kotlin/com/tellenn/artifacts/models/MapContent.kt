package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

class MapContent(
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("code") val code: String
) {
}
