package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Date

@Suppress("unused")
class Annoucement(
    @param:JsonProperty("message") val message: String,
    @param:JsonProperty("created_at") val createdAt: Date
    ) {

}
