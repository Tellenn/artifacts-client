package com.tellenn.artifacts.config

class CharacterConfig(
    val name: String,
    val skin: String,
    val job: String
) {
    companion object {
        fun getPredefinedCharacters(): List<CharacterConfig> {
            return listOf(
                CharacterConfig("Renoir", "men1", "crafter"),
                CharacterConfig("Cloud", "men2", "fighter"),
                CharacterConfig("Aerith", "women1", "alchemist"),
                CharacterConfig("Kepo", "women2", "miner"),
                CharacterConfig("Gustave", "men3", "woodworker")
            )
        }

        fun getPredefinedCharactersMap(): Map<String, CharacterConfig> {
            val map = HashMap<String, CharacterConfig>()
            getPredefinedCharacters().forEach { map[it.name] = it }
            return map
        }
    }
}
