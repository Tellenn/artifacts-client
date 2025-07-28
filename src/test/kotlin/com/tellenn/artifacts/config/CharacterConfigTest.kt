package com.tellenn.artifacts.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CharacterConfigTest {

    @Test
    fun `getPredefinedCharacters should return correct list of characters`() {
        // When
        val characters = CharacterConfig.getPredefinedCharacters()
        
        // Then
        assertEquals(5, characters.size, "Should return 5 characters")
        // Verify Renoir
        val renoir = characters.find { it.name == "Renoir" }
        assertNotNull(renoir, "Renoir should be in the list")
        assertEquals("men1", renoir?.skin, "Renoir should have skin 'men1'")
        assertEquals("crafter", renoir?.job, "Renoir should have job 'crafter'")
        
        // Verify Cloud
        val cloud = characters.find { it.name == "Cloud" }
        assertNotNull(cloud, "Cloud should be in the list")
        assertEquals("men2", cloud?.skin, "Cloud should have skin 'men2'")
        assertEquals("fighter", cloud?.job, "Cloud should have job 'fighter'")
        
        // Verify Aerith
        val aerith = characters.find { it.name == "Aerith" }
        assertNotNull(aerith, "Aerith should be in the list")
        assertEquals("women1", aerith?.skin, "Aerith should have skin 'women1'")
        assertEquals("alchemist", aerith?.job, "Aerith should have job 'alchemist'")
        
        // Verify Kepo
        val kepo = characters.find { it.name == "Kepo" }
        assertNotNull(kepo, "Kepo should be in the list")
        assertEquals("women2", kepo?.skin, "Kepo should have skin 'men3'")
        assertEquals("miner", kepo?.job, "Kepo should have job 'miner'")
        
        // Verify Gustave
        val gustave = characters.find { it.name == "Gustave" }
        assertNotNull(gustave, "Gustave should be in the list")
        assertEquals("men3", gustave?.skin, "Gustave should have skin 'women2'")
        assertEquals("woodworker", gustave?.job, "Gustave should have job 'woodworker'")
    }
}