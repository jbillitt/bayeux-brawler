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
    fun throneAndBrawlSelectionOrder() {
        for (seed in 1L..10L) {
            assertEquals(Family.THRONE, resolveSongSpec(seed, emptyList(), brawl = true, throne = true).family)
            assertEquals(Family.BRAWL, resolveSongSpec(seed, emptyList(), brawl = true, throne = false).family)
            val normal = resolveSongSpec(seed, emptyList()).family
            assertTrue("BRAWL/THRONE must never appear in rotation", normal != Family.BRAWL && normal != Family.THRONE)
        }
    }

    @Test
    fun brawlThemeIsSpeedMetalShaped() {
        for (seed in 1L..10L) {
            val spec = resolveSongSpec(seed, emptyList(), brawl = true)
            assertTrue("brawl bpm ${spec.bpm}", spec.bpm in 168..184)
            assertEquals(4, spec.beatsPerBar)
            assertEquals(8, spec.ground.size)
            assertTrue("minor mode wanted, got ${spec.mode}", spec.mode == Mode.AEOLIAN || spec.mode == Mode.PHRYGIAN)
        }
    }

    @Test
    fun throneThemeIsSlowAndDark() {
        for (seed in 1L..10L) {
            val spec = resolveSongSpec(seed, emptyList(), throne = true)
            assertTrue("throne bpm ${spec.bpm}", spec.bpm in 66..76)
            assertTrue(spec.mode == Mode.DORIAN || spec.mode == Mode.AEOLIAN)
            assertEquals(8, spec.ground.size)
            // Descending tetrachord head: i - bVII - bVI - V
            assertEquals(listOf(0, 6, 5, 4), spec.ground.take(4).map { it.bassDegree })
        }
    }

    @Test
    fun moodsDoNotBendThemedFamilies() {
        val spec = resolveSongSpec(7L, listOf("More Tempo", "More Tempo", "Merrier"), brawl = true)
        assertTrue("moods must not push brawl off 168-184, got ${spec.bpm}", spec.bpm in 168..184)
        assertTrue(spec.mode == Mode.AEOLIAN || spec.mode == Mode.PHRYGIAN)
    }

    @Test
    fun brawlComposeIsAudible() {
        val buf = ProceduralMedievalComposer.compose(7L, 1, false, 22050, emptyList(), brawl = true)
        assertTrue(buf.any { it != 0.toShort() })
    }

    @Test
    fun brawlPlanIsAllInByLevelOne() {
        val spec = resolveSongSpec(7L, emptyList(), brawl = true)
        val plan = planOrchestration(spec, hasTrumpeter = false, rng = orchRng(7L))
        val voices = plan.assignments.map { it.voice }.toSet()
        assertTrue("no harp in the pit", Voice.HARP !in voices)
        assertTrue("no sparkle bells/psaltery", plan.assignments.none { (it.voice == Voice.BELLS || it.voice == Voice.PSALTERY) })
        assertTrue("no destiny fanfare", !plan.destinyFanfare)
        val l1 = activeAssignments(plan, 1).map { it.voice }
        assertTrue("drums+lead from the first bell: $l1",
            Voice.NAKERS in l1 && Voice.TABOR in l1 && Voice.SHAWM in l1 && Voice.GURDY in l1)
    }

    @Test
    fun brawlNakersGallopEveryBar() {
        val spec = resolveSongSpec(7L, emptyList(), brawl = true)
        val events = percussionEvents(spec, Voice.NAKERS, wilder = true)
        // 8 eighth-note hits per 4/4 bar, every bar — the engine of the theme.
        assertEquals(8 * spec.totalBars, events.size)
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
