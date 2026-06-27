package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.models.Raid
import com.tellenn.artifacts.models.RaidRewards
import com.tellenn.artifacts.models.RaidSchedule
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Persisted static raid definition. Dynamic fields (status, instances, next start)
 * are always read live from the API, so they are intentionally not stored here.
 */
@Document(collection = "raids")
data class RaidDocument(
    @Id val code: String,
    val name: String,
    val description: String?,
    val monster: String,
    val schedule: RaidSchedule,
    val rewards: RaidRewards?,
) {
    companion object {
        fun fromRaid(raid: Raid): RaidDocument = RaidDocument(
            code = raid.code,
            name = raid.name,
            description = raid.description,
            monster = raid.monster,
            schedule = raid.schedule,
            rewards = raid.rewards,
        )

        fun toRaid(doc: RaidDocument): Raid = Raid(
            code = doc.code,
            name = doc.name,
            description = doc.description,
            monster = doc.monster,
            schedule = doc.schedule,
            rewards = doc.rewards,
        )
    }
}
