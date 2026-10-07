package com.foleyit.itflow.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayTextTest {
    @Test
    fun `initial of a normal name is its uppercased first letter`() {
        assertEquals("N", initialOf("northwind"))
        assertEquals("A", initialOf("  acme"))
    }

    @Test
    fun `initial of empty, blank or null name is a placeholder instead of throwing`() {
        assertEquals("?", initialOf(""))
        assertEquals("?", initialOf("   "))
        assertEquals("?", initialOf(null))
    }

    @Test
    fun `blank names fall back to the supplied placeholder`() {
        assertEquals("Unnamed client", nameOrFallback("", "Unnamed client"))
        assertEquals("Unnamed client", nameOrFallback(" ", "Unnamed client"))
        assertEquals("Unnamed client", nameOrFallback(null, "Unnamed client"))
        assertEquals("Acme", nameOrFallback("Acme", "Unnamed client"))
    }
}
