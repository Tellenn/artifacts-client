package com.tellenn.artifacts.services

import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ItemDetails
import org.apache.logging.log4j.LogManager
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

/**
 * Service responsible for identifying and tracking rare unique artifacts that require boss drops.
 * Maintains a target stock of [TARGET_QUANTITY] per artifact type, covering one per character.
 */
@Service
class UniqueArtifactService(
    private val itemRepository: ItemRepository,
    private val bankService: BankService,
    private val monsterService: MonsterService,
) {
    companion object {
        /** One artifact per character (5 characters total). */
        const val TARGET_QUANTITY = 5
        private val log = LogManager.getLogger(UniqueArtifactService::class.java)
    }

    /**
     * Returns craftable boss-drop artifacts whose bank stock is below [TARGET_QUANTITY],
     * paired with the number still needed.
     */
    fun findArtifactsToGather(): List<Pair<ItemDetails, Int>> =
        findCraftableBossArtifacts().mapNotNull { artifact ->
            val inBank = bankService.getOne(artifact.code).quantity
            val needed = TARGET_QUANTITY - inBank
            if (needed > 0) {
                log.debug("Artifact ${artifact.code}: $inBank/$TARGET_QUANTITY in bank, need $needed more")
                artifact to needed
            } else null
        }

    /**
     * Finds all artifact-type items that are craftable and whose recipe contains
     * at least one ingredient dropped exclusively by a boss monster.
     */
    private fun findCraftableBossArtifacts(): List<ItemDetails> =
        itemRepository.findByType("artifact", PageRequest.of(0, 200)).content
            .filter { it.craft != null }
            .filter { artifact ->
                artifact.craft!!.items.any { ingredient ->
                    monsterService.findMonsterThatDrop(ingredient.code)?.type == "boss"
                }
            }
}
