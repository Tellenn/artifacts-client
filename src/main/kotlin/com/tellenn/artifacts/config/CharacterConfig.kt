package com.tellenn.artifacts.config

class CharacterConfig(
    val name: String,
    val skin: String,
    val job: String
) {
    companion object {
        fun getPredefinedCharacters(): List<CharacterConfig> {
            return listOf(
                CharacterConfig("Tellenn", "men1", "crafter"),
                CharacterConfig("Cloud", "men2", "fighter"),
                CharacterConfig("Aerith", "women1", "alchemist"),
                CharacterConfig("Kepo", "men3", "miner"),
                CharacterConfig("Evandra", "women2", "woodworker")
            )
        }
    }
}
