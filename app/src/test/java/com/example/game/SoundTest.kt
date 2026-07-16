package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun moodStackingStaysCappedAndInRegister() {
        // Moods used to stack unbounded across runs: tempo multiplied past playable and Nobler
        // dragged the key below the counter-voice register (out-of-key clamped notes = discord).
        val moods = List(6) { "More Tempo" } + List(4) { "Nobler" } + List(3) { "Wilder" }
        for (seed in 1L..20L) {
            val spec = resolveSongSpec(seed, moods)
            assertTrue("bpm ${spec.bpm} over cap", spec.bpm <= 150)
            assertTrue("final ${spec.finalMidi} below register", spec.finalMidi >= 39)
        }
    }

    @Test
    fun brawlMoodIsFastMinorAndAudible() {
        val plain = resolveSongSpec(7L, emptyList())
        val brawl = resolveSongSpec(7L, listOf("Brawl"))
        assertEquals(Mode.AEOLIAN, brawl.mode)
        assertTrue("brawl should be faster", brawl.bpm > plain.bpm)
        // Same run seed keeps the same family/ground — the tune is metalized, not replaced.
        assertEquals(plain.family, brawl.family)
        assertEquals(plain.ground, brawl.ground)
        val buf = ProceduralMedievalComposer.compose(7L, 1, false, 22050, listOf("Brawl"))
        assertTrue(buf.any { it != 0.toShort() })
    }

    @Test
    fun everyVoiceRendersAudibleSound() {
        val rng = kotlin.random.Random(1)
        for (voice in Voice.values()) {
            // Short note exercises perc/buzz paths, long note the drone/sustain paths.
            for (dur in floatArrayOf(0.1f, 1.0f)) {
                val buf = renderNote(voice, 57, dur, 0.9f, 22050, rng)
                var peak = 0f
                for (v in buf) peak = maxOf(peak, Math.abs(v))
                assertTrue("$voice at ${dur}s is silent (peak $peak)", peak > 0.05f)
                assertTrue("$voice at ${dur}s clips (peak $peak)", peak <= 1.0f)
            }
        }
    }

    @Test
    fun estampieFamilyResolvesSanely() {
        // Sweep seeds until each family shows up; estampie must be fast duple with a valid ground.
        var sawEstampie = false
        for (seed in 1L..200L) {
            val spec = resolveSongSpec(seed, emptyList())
            if (spec.family == Family.ESTAMPIE) {
                sawEstampie = true
                assertEquals(4, spec.beatsPerBar)
                assertTrue(spec.bpm in 112..136)
                assertEquals(8, spec.ground.size)
                assertEquals(0, spec.ground.last().bassDegree)  // grounds must close on the final
            }
        }
        assertTrue("estampie never rolled in 200 seeds", sawEstampie)
    }
}
