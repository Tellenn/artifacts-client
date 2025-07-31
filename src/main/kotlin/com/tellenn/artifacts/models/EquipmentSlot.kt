package com.tellenn.artifacts.models

import java.util.Locale
import java.util.Locale.getDefault

enum class EquipmentSlot {
    WEAPON_SLOT,
    SHIELD_SLOT,
    HELMET_SLOT,
    BODY_ARMOR_SLOT,
    LEG_ARMOR_SLOT,
    BOOTS_SLOT,
    RING1_SLOT,
    RING2_SLOT,
    AMULET_SLOT,
    ARTIFACT1_SLOT,
    ARTIFACT2_SLOT,
    ARTIFACT3_SLOT,
    UTILITY1_SLOT,
    UTILITY2_SLOT,
    BAG_SLOT,
    RUNE_SLOT;

    override fun toString(): String {
        return name.lowercase()
    }
}