package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Date

class MapContent(
    @JsonProperty("type") val type: String,
    @JsonProperty("code") val code: String
) {
}
