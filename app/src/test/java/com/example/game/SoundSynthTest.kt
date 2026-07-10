package com.example.game

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoundSynthTest {

    @Test
    fun `sound types exist and can be referenced`() {
        assertNotNull(SoundType.CLANG)
        assertNotNull(SoundType.SWOOSH)
        assertNotNull(SoundType.THWACK)
        assertNotNull(SoundType.OUCH)
        assertNotNull(SoundType.HUZZAH)
    }

    @Test
    fun `generateRhythm returns correct measures`() {
        val totalMeasures = 16
        val rhythms = ProceduralMedievalComposer.generateRhythm(seed = 12345L, totalMeasures = totalMeasures)

        assertEquals("Should return exactly the number of measures requested", totalMeasures, rhythms.size)
        
        // Every measure 3, 7, 11, 15 (0-indexed) should be a long note (1.0f) based on the logic
        for (m in 0 until totalMeasures) {
            val sum = rhythms[m].sum()
            assertEquals("Rhythm notes should roughly sum up to 1 measure", 1.0f, sum, 0.05f)
            
            if (m % 4 == 3) {
                val isValidPattern = rhythms[m] == listOf(1.0f) || 
                                     rhythms[m] == rhythms[2] || // motifAnswer
                                     rhythms[m] == listOf(0.5f, 0.5f)
                assertTrue("Every 4th measure should be a valid varied rhythm", isValidPattern)
            }
        }
    }

    @Test
    fun `generateRhythm provides variance`() {
        // Test that different seeds produce different rhythms
        val rhythmsSeedA = ProceduralMedievalComposer.generateRhythm(seed = 1L, totalMeasures = 8)
        val rhythmsSeedB = ProceduralMedievalComposer.generateRhythm(seed = 42L, totalMeasures = 8)

        // It is highly probable these are different. We can assert they are not identical.
        var isDifferent = false
        for (m in 0 until 8) {
            if (rhythmsSeedA[m] != rhythmsSeedB[m]) {
                isDifferent = true
                break
            }
        }
        
        assertTrue("Different seeds should produce different rhythm structures", isDifferent)
    }

    @Test
    fun `compose does not throw`() {
        // We test a short generation just to ensure no OutOfBounds or Math exceptions occur.
        // For tests, use a low sample rate (like 8000) so it runs faster.
        try {
            val buffer = ProceduralMedievalComposer.compose(
                seed = 999L, 
                level = 10, 
                hasTrumpeter = true, 
                sampleRate = 8000, 
                moods = listOf("More Tempo", "Happier", "Complex Drums")
            )
            assertNotNull("Buffer should not be null", buffer)
            assertTrue("Buffer should have content", buffer.isNotEmpty())
        } catch (e: Exception) {
            fail("Composer threw an exception during generation: ${e.message}")
        }
    }
}
