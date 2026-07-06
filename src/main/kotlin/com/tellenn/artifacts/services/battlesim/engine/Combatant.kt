package com.tellenn.artifacts.services.battlesim.engine

import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.MonsterData
import com.tellenn.artifacts.services.battlesim.model.ActiveEffect
import com.tellenn.artifacts.services.battlesim.model.HealPotion

class Combatant(
    val name: String,
    val isMonster: Boolean,
    var hp: Int,
    var maxHp: Int,
    var attackFire: Int, var attackEarth: Int, var attackWater: Int, var attackAir: Int,
    var dmgGlobal: Int,
    var dmgFire: Int, var dmgEarth: Int, var dmgWater: Int, var dmgAir: Int,
    var resFire: Int, var resEarth: Int, var resWater: Int, var resAir: Int,
    val criticalStrike: Int,
    val initiative: Int,
    val threat: Int,
    val effects: List<ActiveEffect>,
    var healPotion1: HealPotion? = null,
    var healPotion2: HealPotion? = null,
) {
    var turnsPlayed = 0
    var bonusDamagePct = 0
    var poisonStack = 0
    var burnDamageLeft = 0
    var barrierHp = 0
    var greedThresholdsCrossed = 0
    var shellResTurnsLeft = 0
    var shellUsed = false
    var shellPendingRes = 0
    var berserkUsed = false
    var guardActivations = 0
    var lastMirrorTurn = -99
    var firstHitTakenThisTurn = true

    val isAlive: Boolean get() = hp > 0
    fun hpRatio(): Double = if (maxHp == 0) 0.0 else hp.toDouble() / maxHp.toDouble()
    fun healUpTo(amount: Int) { hp = (hp + amount).coerceAtMost(maxHp) }

    companion object {
        fun fromMonster(m: MonsterData): Combatant = Combatant(
            name = m.code, isMonster = true, hp = m.hp, maxHp = m.hp,
            attackFire = m.attackFire, attackEarth = m.attackEarth,
            attackWater = m.attackWater, attackAir = m.attackAir,
            dmgGlobal = 0, dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0,
            resFire = m.defenseFire, resEarth = m.defenseEarth,
            resWater = m.defenseWater, resAir = m.defenseAir,
            criticalStrike = m.criticalStrike, initiative = m.initiative, threat = 0,
            effects = m.effects.map { ActiveEffect(it.code, it.value) },
        )

        fun fromCharacter(
            c: ArtifactsCharacter,
            effects: List<ActiveEffect>,
            healPotion1: HealPotion?,
            healPotion2: HealPotion?,
        ): Combatant = Combatant(
            name = c.name, isMonster = false, hp = c.maxHp, maxHp = c.maxHp,
            attackFire = c.attackFire, attackEarth = c.attackEarth,
            attackWater = c.attackWater, attackAir = c.attackAir,
            dmgGlobal = c.dmg, dmgFire = c.dmgFire, dmgEarth = c.dmgEarth,
            dmgWater = c.dmgWater, dmgAir = c.dmgAir,
            resFire = c.resFire, resEarth = c.resEarth,
            resWater = c.resWater, resAir = c.resAir,
            criticalStrike = c.criticalStrike, initiative = c.initiative, threat = c.threat,
            effects = effects, healPotion1 = healPotion1, healPotion2 = healPotion2,
        )
    }
}
