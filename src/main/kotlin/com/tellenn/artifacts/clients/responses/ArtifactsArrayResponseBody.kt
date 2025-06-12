package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias

class ArtifactsArrayResponseBody<T>(
    @JsonAlias("data") val data: List<T>,
    val total: Int,
    val page: Int,
    val size: Int,
    val pages: Int
)