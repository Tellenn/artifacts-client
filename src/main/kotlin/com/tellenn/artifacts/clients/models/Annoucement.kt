package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias
import java.util.Date

@Suppress("unused")
class Annoucement(
    @JsonAlias("message") val message: String,
    @JsonAlias("created_at") val createdAt: Date
    ) {

}
