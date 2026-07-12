package com.example.game

import org.junit.Assert.assertNotNull
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
        assertNotNull(SoundType.VICTORY_FANFARE)
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
