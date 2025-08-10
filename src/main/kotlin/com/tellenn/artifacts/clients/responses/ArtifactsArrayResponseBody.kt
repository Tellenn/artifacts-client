package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty

class ArtifactsArrayResponseBody<T>(
    @JsonProperty("data") val data: List<T>,
    val total: Int,
    val page: Int,
    val size: Int,
    val pages: Int
)