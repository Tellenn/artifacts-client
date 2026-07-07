package com.tellenn.artifacts.services.battlesim.loadout

import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.services.battlesim.model.ActiveEffect
import com.tellenn.artifacts.services.battlesim.model.HealPotion
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class ResolvedLoadout(
    val effects: List<ActiveEffect>,
    val healPotion1: HealPotion?,
    val healPotion2: HealPotion?,
)

@Service
class CombatEffectResolver(private val itemRepository: ItemRepository) {
    private val logger = LoggerFactory.getLogger(CombatEffectResolver::class.java)

    private val equipmentSlots = listOf(
        "weapon_slot", "rune_slot", "shield_slot", "helmet_slot", "body_armor_slot",
        "leg_armor_slot", "boots_slot", "ring1_slot", "ring2_slot", "amulet_slot",
        "artifact1_slot", "artifact2_slot", "artifact3_slot",
    )

    fun resolve(c: ArtifactsCharacter): ResolvedLoadout {
        val effects = mutableListOf<ActiveEffect>()

        equipmentSlots.mapNotNull { c[it] }.filter { it.isNotBlank() }.forEach { code ->
            lookup(code)?.effects?.filter { it.value != 0 }
                ?.filter { isCombatEffect(it.code) }
                ?.forEach { effects.add(ActiveEffect(it.code, it.value)) }
        }

        val healPotion1 = resolveUtility(c.utility1Slot, c.utility1SlotQuantity, effects)
        val healPotion2 = resolveUtility(c.utility2Slot, c.utility2SlotQuantity, effects)

        return ResolvedLoadout(effects, healPotion1, healPotion2)
    }

    private fun resolveUtility(
        code: String, quantity: Int, effects: MutableList<ActiveEffect>,
    ): HealPotion? {
        if (code.isBlank() || quantity <= 0) return null
        val item = lookup(code) ?: return null
        var healPotion: HealPotion? = null
        item.effects?.forEach { e ->
            when {
                e.code == "heal" -> healPotion = HealPotion(code, e.value, quantity)
                e.code.startsWith("boost_") -> effects.add(ActiveEffect(e.code, e.value))
            }
        }
        return healPotion
    }

    private fun lookup(code: String): ItemDetails? = try {
        itemRepository.findByCode(code)
    } catch (e: Exception) {
        logger.warn("Item '{}' not found for battle simulation loadout: {}", code, e.message)
        null
    }

    private fun isCombatEffect(code: String): Boolean = code in COMBAT_EFFECT_CODES

    companion object {
        // Behavioural combat effects that are NOT plain aggregated stats.
        val COMBAT_EFFECT_CODES = setOf(
            "poison", "burn", "antipoison", "lifesteal", "restore", "splash_restore",
            "healing", "reconstitution", "void_drain", "healing_aura", "sun_shield", "barrier",
            "shell", "corrupted", "enchanted_mirror", "protective_bubble", "greed",
            "berserker_rage", "frenzy", "christmas_magic", "vampiric_strike", "guard",
        )
    }
}
