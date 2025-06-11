package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias
import java.time.Instant

@Suppress("unused")
class ServerStatus(
    @JsonAlias("status") val status : String,
    @JsonAlias("version") val version : String,
    @JsonAlias("max_level") val maxLevel : Int,
    @JsonAlias("characters_online") val charactersOnline : Int,
    @JsonAlias("server_time") val serverTime : Instant,
    @JsonAlias("announcements") val announcements : Array<Annoucement>,
    @JsonAlias("next_wipe") val nextWipe : String,
    @JsonAlias("last_wipe") val lastWipe : String,
) {
}