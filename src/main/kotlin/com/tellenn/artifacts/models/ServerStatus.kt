package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@Suppress("unused")
class ServerStatus(
    @param:JsonProperty("version") val version : String,
    @param:JsonProperty("max_level") val maxLevel : Int,
    @param:JsonProperty("max_skill_level") val maxSkillLevel : Int,
    @param:JsonProperty("characters_online") val charactersOnline : Int,
    @param:JsonProperty("season") val season : SeasonInfo,
    @param:JsonProperty("server_time") val serverTime : Instant,
    @param:JsonProperty("rate_limits") val rateLimits : Array<RateLimit>
)

@Suppress("unused")
class SeasonInfo(
    @param:JsonProperty("name") val name : String,
    @param:JsonProperty("number") val number : Int,
    @param:JsonProperty("start_date") val startDate : Instant,
    @param:JsonProperty("badges") val badges : Array<Badge>,
    @param:JsonProperty("skins") val skins : Array<Badge>
)

@Suppress("unused")
class Badge(
    @param:JsonProperty("code") val code : String,
    @param:JsonProperty("description") val description : String,
    @param:JsonProperty("required_points") val requiredPoints : Int,
)

@Suppress("unused")
class RateLimit(
    @param:JsonProperty("type") val type : String,
    @param:JsonProperty("value") val value : String,
)
