package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Date

@Suppress("unused")
class Annoucement(
    @JsonProperty("message") val message: String,
    @JsonProperty("created_at") val createdAt: Date
    ) {

}
