package com.tellenn.artifacts.clients.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tellenn.config.Const;
import com.tellenn.utils.ItemUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtifactsCharacter(
        String name,
        int level,
        int gold,
        int hp,
        @JsonAlias("max_hp") int maxHp,
        int x,
        int y,
        InventorySlot[] inventory,
        int cooldown,
        String skin,
        String task,
        @JsonAlias("task_type") String taskType,
        @JsonAlias("task_total") int taskTotal,
        @JsonAlias("task_progress") int taskProgress,
        @JsonAlias("mining_level") int miningLevel,
        @JsonAlias("woodcutting_level") int woodcuttingLevel,
        @JsonAlias("fishing_level") int fishingLevel,
        @JsonAlias("weaponcrafting_level") int weaponcraftingLevel,
        @JsonAlias("gearcrafting_level") int gearcraftingLevel,
        @JsonAlias("jewelrycrafting_level") int jewelrycraftingLevel,
        @JsonAlias("cooking_level") int cookingLevel,
        @JsonAlias("alchemy_level") int alchemyLevel,
        @JsonAlias("inventory_max_items") int inventoryMaxItems,
        @JsonAlias("attack_fire") int attackFire,
        @JsonAlias("attack_earth") int attackEarth,
        @JsonAlias("attack_water") int attackWater,
        @JsonAlias("attack_air") int attackAir,
        @JsonAlias("dmg_fire") int dmgFire,
        @JsonAlias("dmg_earth") int dmgEarth,
        @JsonAlias("dmg_water") int dmgWater,
        @JsonAlias("dmg_air") int dmgAir,
        @JsonAlias("res_fire") int resFire,
        @JsonAlias("res_earth") int resEarth,
        @JsonAlias("res_water") int resWater,
        @JsonAlias("res_air") int resAir,
        @JsonAlias("weapon_slot") String weaponSlot,
        @JsonAlias("rune_slot") String runeSlot,
        @JsonAlias("shield_slot") String shieldSlot,
        @JsonAlias("helmet_slot") String helmetSlot,
        @JsonAlias("body_armor_slot") String bodyArmorSlot,
        @JsonAlias("leg_armor_slot") String legArmorSlot,
        @JsonAlias("boots_slot") String bootsSlot,
        @JsonAlias("ring1_slot") String ring1Slot,
        @JsonAlias("ring2_slot") String ring2Slot,
        @JsonAlias("amulet_slot") String amuletSlot,
        @JsonAlias("artifact1_slot") String artifact1Slot,
        @JsonAlias("artifact2_slot") String artifact2Slot,
        @JsonAlias("artifact3_slot") String artifact3Slot,
        @JsonAlias("utility1_slot") String utility1Slot,
        @JsonAlias("utility1_slot_quantity") int utility1SlotQuantity,
        @JsonAlias("utility2_slot") String utility2Slot,
        @JsonAlias("utility2_slot_quantity") int utility2SlotQuantity,
        @JsonAlias("bag_slot") String bagSlot ){

    public ArtifactsCharacter(ArtifactsCharacter character, String utility1Slot, String utility2Slot) {
        this(
                character.name(),
                character.level(),
                character.gold(),
                character.hp(),
                character.maxHp(),
                character.x(),
                character.y(),
                character.inventory(),
                character.cooldown(),
                character.skin(),
                character.task(),
                character.taskType(),
                character.taskTotal(),
                character.taskProgress(),
                character.miningLevel(),
                character.woodcuttingLevel(),
                character.fishingLevel(),
                character.weaponcraftingLevel(),
                character.gearcraftingLevel(),
                character.jewelrycraftingLevel(),
                character.cookingLevel(),
                character.alchemyLevel(),
                character.inventoryMaxItems(),
                character.attackFire(),
                character.attackEarth(),
                character.attackWater(),
                character.attackAir(),
                character.dmgFire(),
                character.dmgEarth(),
                character.dmgWater(),
                character.dmgAir(),
                character.resFire(),
                character.resEarth(),
                character.resWater(),
                character.resAir(),
                character.weaponSlot(),
                character.runeSlot(),
                character.shieldSlot(),
                character.helmetSlot(),
                character.bodyArmorSlot(),
                character.legArmorSlot(),
                character.bootsSlot(),
                character.ring1Slot(),
                character.ring2Slot(),
                character.amuletSlot(),
                character.artifact1Slot(),
                character.artifact2Slot(),
                character.artifact3Slot(),
                utility1Slot,
                100,
                utility2Slot,
                100,
                character.bagSlot()
        );
    }

    public String position() {
        return "{\"x\": "+this.x+" , \"y\": "+this.y+"}";
    }

    public int getRemainingTask(){
        return taskTotal - taskProgress;
    }

    public int getLevelOf(String job) {
        return switch (job) {
            case "mining" -> miningLevel;
            case "woodcutting" -> woodcuttingLevel;
            case "fishing" -> fishingLevel;
            case "cooking" -> cookingLevel;
            case "weaponcrafting" -> weaponcraftingLevel;
            case "jewelrycrafting" -> jewelrycraftingLevel;
            case "gearcrafting" -> gearcraftingLevel;
            case "alchemy" -> alchemyLevel;
            default -> 0;
        };
    }

    /**
     * Return the lowest level ranked by 5 levels in order weapon > gear > jewel
     * @Example : Weapon 23, Gear 24, jewel 20 returns weapon
     * @Example : Weapon 23, Gear 19, jewel 20 returns gear
<     * @return the lowest skill level, floored by 5, with priority.
     */
    public String getLowestCraftLevel() {
        int modulo = 5;
        int min = Math.min(Math.min(weaponcraftingLevel - (weaponcraftingLevel % modulo), jewelrycraftingLevel - (jewelrycraftingLevel % modulo)), gearcraftingLevel - (gearcraftingLevel % modulo));
        if (min == Const.MAX_LEVEL) return "none";
        if (min == weaponcraftingLevel - (weaponcraftingLevel % modulo)) return "weaponcrafting";
        if (min == gearcraftingLevel - (gearcraftingLevel % modulo)) return "gearcrafting";
        return "jewelrycrafting";

    }

    public int getFreeInventorySlots() {
        AtomicInteger slots = new AtomicInteger(inventoryMaxItems);
        Arrays.stream(inventory).toList().forEach(e -> slots.set(slots.get() - e.quantity()));
        return slots.get();
    }

    public String get(String equipmentType) {
        return switch (equipmentType) {
            case "weapon_slot" -> weaponSlot;
            case "shield_slot" -> shieldSlot;
            case "helmet_slot" -> helmetSlot;
            case "body_armor_slot" -> bodyArmorSlot;
            case "leg_armor_slot" -> legArmorSlot;
            case "boots_slot" -> bootsSlot;
            case "ring1_slot" -> ring1Slot;
            case "ring2_slot" -> ring2Slot;
            case "amulet_slot" -> amuletSlot;
            case "artifact1_slot" -> artifact1Slot;
            case "artifact2_slot" -> artifact2Slot;
            case "artifact3_slot" -> artifact3Slot;
            default -> null;
        };
    }

    public ItemInformation getHealingItems(ItemUtils itemUtils) {
        for (InventorySlot slot : inventory) {
            if (slot.quantity() > 0) {
                ItemInformation item = itemUtils.getItemInfo(slot.code());
                if (item.effects() != null) {
                    for (Effect effect : item.effects()) {
                        if (effect.code().equals("heal")) {
                            return item;
                        }
                    }
                }
            }
        }
        return null;
    }

    public String getStrongestAttackElement() {
        int highest = 0;
        if (attackAir > 0) highest = attackAir;
        if (attackFire > 0 && highest < attackFire) highest = attackFire;
        if (attackWater > 0 && highest < attackWater) highest = attackWater;
        if (attackEarth > 0 && highest < attackEarth) highest = attackEarth;

        if (attackAir == highest) return "air";
        if (attackFire == highest) return "fire";
        if (attackWater == highest) return "water";
        return "earth";
    }

    public List<ItemInformation> getAllEquipment(ItemUtils itemUtils) {
        List<String> codes = List.of(weaponSlot, shieldSlot, helmetSlot, bodyArmorSlot, legArmorSlot, bootsSlot, ring1Slot, ring2Slot, amuletSlot, artifact1Slot, artifact2Slot, artifact3Slot);
        return codes.stream().filter(s -> s != null && !s.isEmpty() ).map(itemUtils::getItemInfo).toList();
    }
}

