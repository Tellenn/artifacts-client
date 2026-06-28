package com.tellenn.artifacts.config

class CharacterConfig(
    val name: String,
    val skin: String,
    val job: String
) {
    companion object {
        fun getPredefinedCharacters(): List<CharacterConfig> {
            return listOf(
                CharacterConfig("Renoir", "robin_hood", "crafter"),
                CharacterConfig("Cloud", "gold_founder", "fighter"),
                CharacterConfig("Aerith", "necromancer", "alchemist"),
                CharacterConfig("Kepo", "vip_founder", "miner"),
                CharacterConfig("Gustave", "zombie", "woodworker")
            )
        }

        fun getPredefinedCharactersMap(): Map<String, CharacterConfig> {
            val map = HashMap<String, CharacterConfig>()
            getPredefinedCharacters().forEach { map[it.name] = it }
            return map
        }
    }
}
