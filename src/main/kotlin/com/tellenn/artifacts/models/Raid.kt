package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A raid definition from the Artifacts /raids endpoint.
 * Static fields (code, name, monster, schedule, rewards) are cached; the dynamic
 * fields (status, nextStartAt, instances, participantCount) are only present on live reads.
 */
class Raid(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("description") val description: String?,
    @param:JsonProperty("monster") val monster: String,
    @param:JsonProperty("schedule") val schedule: RaidSchedule,
    @param:JsonProperty("rewards") val rewards: RaidRewards?,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("next_start_at") val nextStartAt: String? = null,
    @param:JsonProperty("participant_count") val participantCount: Int? = null,
    @param:JsonProperty("active_instance") val activeInstance: RaidInstance? = null,
    @param:JsonProperty("latest_instance") val latestInstance: RaidInstance? = null,
)

class RaidSchedule(
    @param:JsonProperty("weekdays") val weekdays: List<String>,
    @param:JsonProperty("start_hour_utc") val startHourUtc: Int,
    @param:JsonProperty("start_minute_utc") val startMinuteUtc: Int,
    @param:JsonProperty("duration_hours") val durationHours: Int,
)

class RaidInstance(
    @param:JsonProperty("starts_at") val startsAt: String?,
    @param:JsonProperty("ends_at") val endsAt: String?,
    @param:JsonProperty("status") val status: String,
    @param:JsonProperty("total_hp") val totalHp: Long,
    @param:JsonProperty("remaining_hp") val remainingHp: Long,
    @param:JsonProperty("participant_count") val participantCount: Int,
    @param:JsonProperty("ended_at") val endedAt: String?,
    @param:JsonProperty("result") val result: String?,
    @param:JsonProperty("rewards_distributed_at") val rewardsDistributedAt: String?,
) {
    /** Finished instances carry a status beginning with "finished" (e.g. finished_success/finished_failure). */
    fun isFinished(): Boolean = status.startsWith("finished")
}

class RaidRewards(
    @param:JsonProperty("damage_rewards") val damageRewards: List<RaidDamageReward>,
    @param:JsonProperty("leaderboard") val leaderboard: List<RaidLeaderboardReward>,
)

class RaidDamageReward(
    @param:JsonProperty("damage_per_reward") val damagePerReward: Long,
    @param:JsonProperty("max_rewards") val maxRewards: Int,
    @param:JsonProperty("items") val items: List<RaidRewardItem>,
)

class RaidLeaderboardReward(
    @param:JsonProperty("min_rank") val minRank: Int,
    @param:JsonProperty("max_rank") val maxRank: Int,
    @param:JsonProperty("items") val items: List<RaidRewardItem>,
)

class RaidRewardItem(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("quantity") val quantity: Int,
)
