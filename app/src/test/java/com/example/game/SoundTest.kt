package com.example.game

import org.junit.Test

class SoundTest {
    @Test
    fun testGenerateMusic() {
        val buffer = ProceduralMedievalComposer.compose(
            seed = 12345L,
            level = 5,
            hasTrumpeter = true,
            sampleRate = 22050
        )
        var max = 0
        var hasNonZero = false
        for (s in buffer) {
            if (s != 0.toShort()) hasNonZero = true
            if (Math.abs(s.toInt()) > max) max = Math.abs(s.toInt())
        }
        println("Generated buffer size: ${buffer.size}, hasNonZero: $hasNonZero, max: $max")
    }
}
