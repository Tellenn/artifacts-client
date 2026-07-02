package com.tellenn.artifacts.services

import com.tellenn.artifacts.config.CharacterConfig
import org.springframework.stereotype.Service

/**
 * Détermine quelle compétence de récolte — et donc quel personnage — est responsable
 * de la production d'un matériau donné.
 *
 * Les matériaux assemblés par le crafter (compétences de craft pures) ne sont pas
 * délégués : le crafter les produit lui-même, donc [skillFor] renvoie `null`.
 */
@Service
class MaterialResponsibility(
    private val itemService: ItemService
) {

    /**
     * Renvoie la compétence de récolte responsable du matériau, ou `null` si le crafter
     * l'assemble lui-même (compétence de craft) ou si le sous-type n'est pas mappable.
     */
    fun skillFor(materialCode: String): String? {
        val item = itemService.getItem(materialCode)
        val craft = item.craft
        return if (craft != null) {
            if (craft.skill in CRAFTER_SKILLS) null else craft.skill
        } else {
            item.subtype.takeIf { it in GATHERING_SUBTYPES }
        }
    }

    /**
     * Renvoie le nom du personnage responsable du matériau, ou `null` si aucun
     * (matériau assemblé par le crafter ou non mappable).
     */
    fun characterFor(materialCode: String): String? {
        val skill = skillFor(materialCode) ?: return null
        val job = SKILL_TO_JOB[skill] ?: return null
        return CharacterConfig.getPredefinedCharacters().firstOrNull { it.job == job }?.name
    }

    companion object {
        private val CRAFTER_SKILLS = setOf("weaponcrafting", "gearcrafting", "jewelrycrafting")
        private val GATHERING_SUBTYPES = setOf("mining", "woodcutting", "fishing", "alchemy", "mob")
        private val SKILL_TO_JOB = mapOf(
            "mining" to "miner",
            "woodcutting" to "woodworker",
            "alchemy" to "alchemist",
            "fishing" to "alchemist",
            "cooking" to "alchemist",
            "mob" to "fighter"
        )
    }
}
