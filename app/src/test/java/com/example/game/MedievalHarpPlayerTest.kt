package com.example.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedievalHarpPlayerTest {
    @Test
    fun compositionAcceptanceRequiresCurrentGenerationAndPlayingState() {
        assertTrue(acceptsComposition(expectedGeneration = 4, currentGeneration = 4, isPlaying = true))
        assertFalse(acceptsComposition(expectedGeneration = 4, currentGeneration = 5, isPlaying = true))
        assertFalse(acceptsComposition(expectedGeneration = 4, currentGeneration = 4, isPlaying = false))
    }
}
