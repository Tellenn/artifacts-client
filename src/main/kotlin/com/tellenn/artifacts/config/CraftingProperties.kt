package com.tellenn.artifacts.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Politique de protection des matériaux rares lors du leveling des compétences de craft.
 *
 * Les matériaux rares (gemmes, cristaux, tissus enchantés) sont réservés aux crafts d'équipement
 * réels : on ne les consomme pour monter une compétence que lorsqu'un *surplus* au-dessus du
 * plancher de réserve est disponible en banque.
 */
@ConfigurationProperties(prefix = "artifacts.crafting")
data class CraftingProperties(
    /** Codes des matériaux considérés comme rares et donc protégés. */
    val rareMaterials: Set<String> = DEFAULT_RARE_MATERIALS,
    /** Plancher de réserve par matériau (surcharge le défaut). Ex. `ruby -> 15`. */
    val rareReserve: Map<String, Int> = emptyMap(),
    /** Plancher de réserve appliqué à un matériau rare absent de [rareReserve]. */
    val defaultRareReserve: Int = 10,
) {
    fun isRare(code: String): Boolean = code in rareMaterials

    fun reserveFloor(code: String): Int = rareReserve[code] ?: defaultRareReserve

    companion object {
        val DEFAULT_RARE_MATERIALS: Set<String> = setOf(
            "magical_cure", "jasper_crystal", "astralyte_crystal", "enchanted_fabric",
            "ruby", "sapphire", "emerald", "topaz", "diamond",
        )
    }
}
