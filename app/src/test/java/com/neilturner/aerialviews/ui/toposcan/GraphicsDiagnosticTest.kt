package com.neilturner.aerialviews.ui.toposcan

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphicsDiagnosticTest {
    private val pattern = List(3) { listOf(0xff0000, 0x00ff00, 0x0000ff, 0xffffff) }.flatten()

    @Test
    fun `known colours pass with a small quantisation tolerance`() {
        assertTrue(GraphicsDiagnostic.matches(pattern))
        assertTrue(GraphicsDiagnostic.matches(pattern.map { it and 0xf8f8f8 }))
    }

    @Test
    fun `black frames and missing samples fail`() {
        assertFalse(GraphicsDiagnostic.matches(List(12) { 0 }))
        assertFalse(GraphicsDiagnostic.matches(emptyList()))
        assertFalse(GraphicsDiagnostic.matches(pattern.take(4)))
    }

    @Test
    fun `cropped viewport and displaced bars fail`() {
        assertFalse(GraphicsDiagnostic.matches(pattern.take(4) + List(8) { 0 }))
        assertFalse(GraphicsDiagnostic.matches(pattern.reversed()))
    }
}
