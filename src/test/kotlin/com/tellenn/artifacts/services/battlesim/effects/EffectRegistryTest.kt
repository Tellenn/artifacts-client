package com.tellenn.artifacts.services.battlesim.effects

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EffectRegistryTest {
    private val dummy = object : EffectHandler { override val code = "poison" }

    @Test
    fun `handlerFor returns the registered handler`() {
        val registry = EffectRegistry(listOf(dummy))
        assertNotNull(registry.handlerFor("poison"))
    }

    @Test
    fun `handlerFor returns null for unknown code`() {
        val registry = EffectRegistry(listOf(dummy))
        assertNull(registry.handlerFor("does_not_exist"))
    }
}
