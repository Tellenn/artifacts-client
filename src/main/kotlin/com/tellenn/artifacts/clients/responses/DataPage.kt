package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias

class DataPage<T>(
    @JsonAlias("items") val items: List<T>,
    @JsonAlias("total") val total: Int,
    @JsonAlias("page") val page: Int,
    @JsonAlias("size") val size: Int,
    @JsonAlias("pages") val pages: Int
)