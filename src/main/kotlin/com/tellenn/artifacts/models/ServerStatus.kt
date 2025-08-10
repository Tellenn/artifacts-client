package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@Suppress("unused")
class ServerStatus(
    @JsonProperty("version") val version : String,
    @JsonProperty("max_level") val maxLevel : Int,
    @JsonProperty("max_skill_level") val maxSkillLevel : Int,
    @JsonProperty("characters_online") val charactersOnline : Int,
    @JsonProperty("season") val season : SeasonInfo,
    @JsonProperty("server_time") val serverTime : Instant,
    @JsonProperty("announcements") val announcements : Array<Annoucement>,
    @JsonProperty("rate_limits") val rateLimits : Array<RateLimit>
) {
}

class SeasonInfo(
    @JsonProperty("name") val name : String,
    @JsonProperty("number") val number : Int,
    @JsonProperty("start_date") val startDate : Instant,
    @JsonProperty("badges") val badges : Array<Badge>,
    @JsonProperty("skins") val skins : Array<Badge>
)

class Badge(
    @JsonProperty("code") val code : String,
    @JsonProperty("description") val description : String,
    @JsonProperty("required_points") val requiredPoints : Int,
)

class RateLimit(
    @JsonProperty("type") val type : String,
    @JsonProperty("value") val value : String,
)

//{
//  "data" : {
//    "version" : "5.0",
//    "server_time" : "2025-07-08T12:04:27.447Z",
//    "max_level" : 45,
//    "max_skill_level" : 45,
//    "characters_online" : 161,
//    "season" : {
//      "name" : "The Demoniac Forest",
//      "number" : 5,
//      "start_date" : "2025-06-29T00:00:00.000Z",
//      "badges" : [ {
//        "code" : "season5_bronze",
//        "description" : "Earn at least 50% of the achievement points to get this badge.",
//        "required_points" : 100
//      }, {
//        "code" : "season5_silver",
//        "description" : "Earn at least 75% of the achievement points to upgrade your bronze badge to silver.",
//        "required_points" : 150
//      }, {
//        "code" : "season5_gold",
//        "description" : "Earn all achievement points to upgrade your silver badge to gold.",
//        "required_points" : 200
//      }, {
//        "code" : "season5_color",
//        "description" : "Be the first to earn all achievement points to upgrade your gold badge to color.",
//        "required_points" : 200
//      } ],
//      "skins" : [ {
//        "code" : "corrupted1",
//        "description" : "Earn all achievement points to unlock this skin.",
//        "required_points" : 200
//      } ]
//    },
//    "announcements" : [ {
//      "message" : "Welcome to Artifacts Season 5. The server is up.",
//      "created_at" : "2025-07-07T22:09:56.536Z"
//    } ],
//    "rate_limits" : [ {
//      "type" : "account",
//      "value" : "2/second"
//    }, {
//      "type" : "account",
//      "value" : "50/hour"
//    }, {
//      "type" : "token",
//      "value" : "2/second"
//    }, {
//      "type" : "token",
//      "value" : "50/hour"
//    }, {
//      "type" : "data",
//      "value" : "32/second"
//    }, {
//      "type" : "data",
//      "value" : "200/minute"
//    }, {
//      "type" : "data",
//      "value" : "7200/hour"
//    }, {
//      "type" : "action",
//      "value" : "7/2second"
//    }, {
//      "type" : "action",
//      "value" : "200/minute"
//    }, {
//      "type" : "action",
//      "value" : "7200/hour"
//    } ]
//  }
//}