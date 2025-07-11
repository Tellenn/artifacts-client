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
        
        // Verify Tellenn
        val tellenn = characters.find { it.name == "Tellenn" }
        assertNotNull(tellenn, "Tellenn should be in the list")
        assertEquals("men1", tellenn?.skin, "Tellenn should have skin 'men1'")
        assertEquals("crafter", tellenn?.job, "Tellenn should have job 'crafter'")
        
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
        assertEquals("men3", kepo?.skin, "Kepo should have skin 'men3'")
        assertEquals("miner", kepo?.job, "Kepo should have job 'miner'")
        
        // Verify Evandra
        val evandra = characters.find { it.name == "Evandra" }
        assertNotNull(evandra, "Evandra should be in the list")
        assertEquals("women2", evandra?.skin, "Evandra should have skin 'women2'")
        assertEquals("woodworker", evandra?.job, "Evandra should have job 'woodworker'")
    }
}