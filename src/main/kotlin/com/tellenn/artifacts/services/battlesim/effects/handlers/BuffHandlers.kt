package com.tellenn.artifacts.services.battlesim.effects.handlers

import com.tellenn.artifacts.services.battlesim.effects.EffectHandler
import com.tellenn.artifacts.services.battlesim.engine.Combatant
import com.tellenn.artifacts.services.battlesim.model.FightContext
import org.springframework.stereotype.Component

@Component
class BoostHpHandler : EffectHandler {
    override val code = "boost_hp"
    override fun onFightStart(ctx: FightContext, owner: Combatant, value: Int) {
        owner.maxHp += value
        owner.hp += value
    }
}

@Component class BoostDmgFireHandler : EffectHandler {
    override val code = "boost_dmg_fire"
    override fun onFightStart(ctx: FightContext, owner: Combatant, value: Int) { owner.dmgFire += value }
}
@Component class BoostDmgEarthHandler : EffectHandler {
    override val code = "boost_dmg_earth"
    override fun onFightStart(ctx: FightContext, owner: Combatant, value: Int) { owner.dmgEarth += value }
}
@Component class BoostDmgWaterHandler : EffectHandler {
    override val code = "boost_dmg_water"
    override fun onFightStart(ctx: FightContext, owner: Combatant, value: Int) { owner.dmgWater += value }
}
@Component class BoostDmgAirHandler : EffectHandler {
    override val code = "boost_dmg_air"
    override fun onFightStart(ctx: FightContext, owner: Combatant, value: Int) { owner.dmgAir += value }
}
@Component class BoostResFireHandler : EffectHandler {
    override val code = "boost_res_fire"
    override fun onFightStart(ctx: FightContext, owner: Combatant, value: Int) { owner.resFire += value }
}
@Component class BoostResEarthHandler : EffectHandler {
    override val code = "boost_res_earth"
    override fun onFightStart(ctx: FightContext, owner: Combatant, value: Int) { owner.resEarth += value }
}
@Component class BoostResWaterHandler : EffectHandler {
    override val code = "boost_res_water"
    override fun onFightStart(ctx: FightContext, owner: Combatant, value: Int) { owner.resWater += value }
}
@Component class BoostResAirHandler : EffectHandler {
    override val code = "boost_res_air"
    override fun onFightStart(ctx: FightContext, owner: Combatant, value: Int) { owner.resAir += value }
}
