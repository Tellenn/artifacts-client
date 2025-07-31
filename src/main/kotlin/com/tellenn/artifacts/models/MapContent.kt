package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonAlias
import java.util.Date

class MapContent(
    @JsonAlias("type") val type: String,
    @JsonAlias("code") val code: String
) {
}
