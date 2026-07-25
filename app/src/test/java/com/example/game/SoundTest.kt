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
            assertTrue(
                "brawl melody must stay in fast note values",
                generateSong(spec, melodyRng(seed)).melody.all { it.durBeats <= 1f }
            )
        }
    }

    @Test
    fun themedGroundsAndPadsResolveWithoutWrongFifths() {
        var sawPhrygian = false
        for (seed in 1L..100L) {
            for (spec in listOf(
                resolveSongSpec(seed, emptyList(), brawl = true),
                resolveSongSpec(seed, emptyList(), throne = true)
            )) {
                assertEquals("${spec.family} must close on its final", 0, spec.ground.last().bassDegree)
                val song = generateSong(spec, melodyRng(seed))
                for (bar in spec.ground.indices) {
                    val expected = groundChordMidis(spec, spec.ground[bar].bassDegree)
                    assertEquals(expected, song.padChords[bar].midis)
                    if (spec.mode == Mode.PHRYGIAN) {
                        sawPhrygian = true
                        assertEquals("BRAWL power chords must not hide a chromatic semitone", 7, expected[1] - expected[0])
                    }
                }
            }
        }
        assertTrue("seed sweep never exercised Phrygian BRAWL", sawPhrygian)
    }

    @Test
    fun brawlRiffUsesOnlyCurrentPowerChord() {
        val spec = (1L..100L)
            .map { resolveSongSpec(it, emptyList(), brawl = true) }
            .first { it.mode == Mode.PHRYGIAN }
        for (event in brawlRiffEvents(spec)) {
            val bar = (event.startBeat / spec.beatsPerBar).toInt()
            val allowed = groundChordMidis(spec, spec.ground[bar % 8].bassDegree)
                .map { Math.floorMod(it, 12) }
                .toSet()
            assertTrue("riff note ${event.midi} is outside bar $bar chord $allowed", Math.floorMod(event.midi, 12) in allowed)
        }
    }

    @Test
    fun throneThemeIsStatelyAndRegal() {
        for (seed in 1L..10L) {
            val spec = resolveSongSpec(seed, emptyList(), throne = true)
            // A processional march, not a cortege — 66-76 read as a funeral.
            assertTrue("throne bpm ${spec.bpm}", spec.bpm in 86..98)
            // Regal, not ominous: major (Ionian) or fanfare-flat-7 (Mixolydian)
            assertTrue(spec.mode == Mode.IONIAN || spec.mode == Mode.MIXOLYDIAN)
            assertEquals(8, spec.ground.size)
            // Must open on the tonic and cadence home; the old grounds all opened on the
            // descending lamento tetrachord (0-6-5-4), which is the Baroque ground for grief.
            assertEquals(0, spec.ground.first().bassDegree)
            assertEquals(0, spec.ground.last().bassDegree)
        }
    }

    /**
     * The whole point of building the strum out of degreeToMidi against the bar's own ground
     * degree: every pitch is a member of the mode by construction, so a rolled chord cannot land
     * on a note outside the key no matter which family, mode or ground the run rolled.
     */
    @Test
    fun everyHarpStrumPitchBelongsToTheMode() {
        val specs = (1L..12L).flatMap { seed ->
            listOf(
                resolveSongSpec(seed, emptyList()),
                resolveSongSpec(seed, emptyList(), brawl = true),
                resolveSongSpec(seed, emptyList(), throne = true)
            )
        }
        for (spec in specs) {
            val scale = spec.mode.steps.toSet()
            for (e in harpStrumEvents(spec)) {
                val pitchClass = Math.floorMod(e.midi - spec.finalMidi, 12)
                assertTrue(
                    "${spec.family}/${spec.mode}: midi ${e.midi} is $pitchClass semitones off the " +
                        "final, which is not in the mode $scale",
                    pitchClass in scale
                )
            }
        }
    }

    @Test
    fun harpStrumsLandOnPhraseEndsAndRollUpward() {
        val spec = resolveSongSpec(3L, emptyList())
        val events = harpStrumEvents(spec)
        assertTrue("a 32-bar piece should get several strums, got ${events.size}", events.size >= 8)

        val bpb = spec.beatsPerBar
        events.forEach {
            val bar = (it.startBeat / bpb).toInt()
            assertEquals("strums belong on the last bar of a four-bar phrase", 3, bar % 4)
        }

        // Within one roll the pitches ascend and the velocity eases off — a hand dragged across
        // the strings, thumb first, not a block chord.
        val firstRoll = events.take(4)
        firstRoll.zipWithNext().forEach { (a, b) ->
            assertTrue("strum should roll upward: ${a.midi} then ${b.midi}", b.midi > a.midi)
            assertTrue("strum should roll in time: ${a.startBeat} then ${b.startBeat}", b.startBeat > a.startBeat)
            assertTrue("strum should ease off up the roll", b.velocity < a.velocity)
        }
    }

    @Test
    fun theHarpOnlyLearnsToStrumAfterAFewLevels() {
        val spec = resolveSongSpec(3L, emptyList())
        val plan = planOrchestration(spec, hasTrumpeter = false, rng = orchRng(3L))
        assertTrue("no strumming at level one",
            activeAssignments(plan, 1).none { it.line == LineRef.STRUM })
        assertTrue("the strum should have arrived by level 6",
            activeAssignments(plan, 6).any { it.line == LineRef.STRUM })
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
    fun fullyLayeredBrawlComposeKeepsHeadroom() {
        val buf = ProceduralMedievalComposer.compose(7L, 11, true, 8000, emptyList(), brawl = true)
        val peak = buf.maxOf { Math.abs(it.toInt()) }
        assertTrue("fully layered BRAWL is too quiet", peak > 1000)
        assertTrue("fully layered BRAWL hit digital full scale", peak < 32767)
    }

    @Test
    fun brawlPlanLayersTheFullMedievalPalette() {
        val spec = resolveSongSpec(7L, emptyList(), brawl = true)
        val plan = planOrchestration(spec, hasTrumpeter = false, rng = orchRng(7L))
        val voices = plan.assignments.map { it.voice }.toSet()
        assertEquals("every existing medieval voice should join by the late game", Voice.values().toSet(), voices)
        assertTrue("no destiny fanfare", !plan.destinyFanfare)
        val l1 = activeAssignments(plan, 1)
        // The opening line-up now rolls per run, so this pins the ROLES that must be present
        // at level one, not the specific instruments filling them.
        assertTrue("level one needs the metal rhythm section: $l1",
            l1.any { it.line == LineRef.RIFF } &&
                l1.any { it.voice == Voice.NAKERS } &&
                l1.any { it.voice == Voice.TABOR } &&
                l1.any { it.voice == Voice.HARP && it.line == LineRef.MELODY } &&
                l1.any { it.line == LineRef.DRONE })
        for (level in 1 until 11) {
            val current = activeAssignments(plan, level).toSet()
            val next = activeAssignments(plan, level + 1).toSet()
            assertTrue("BRAWL must add layers rather than replace level $level", next.containsAll(current))
        }
    }

    @Test
    fun brawlNakersGallopEveryBar() {
        val spec = resolveSongSpec(7L, emptyList(), brawl = true)
        val events = percussionEvents(spec, Voice.NAKERS, wilder = true)
        // 16 sixteenth-note hits per 4/4 bar, every bar — a true double kick. At 8ths this
        // read as one pedal working hard rather than two feet.
        assertEquals(16 * spec.totalBars, events.size)
    }

    @Test
    fun choirEnergyLivesInTheFormantBand() {
        for (phrase in 0..1) {
            val buf = renderNote(Voice.CHOIR, 41, 1.5f, 1f, 44100, kotlin.random.Random(5), phrase)
            val formant = bandRms(buf, 44100, 400f, 3000f)
            val sub = bandRms(buf, 44100, 20f, 300f)
            assertTrue("low choir phrase $phrase formant $formant should dominate sub band $sub", formant > sub)
        }
    }

    @Test
    fun gurdyAndBellHonorRequestedPitch() {
        val sampleRate = 44100
        val midi = 48
        val rootHz = midiHz(midi)
        val gurdy = renderNote(Voice.GURDY, midi, 1f, 1f, sampleRate, kotlin.random.Random(9))
        val rootPower = tonePower(gurdy, sampleRate, rootHz)
        val accidentalFifth = tonePower(gurdy, sampleRate, rootHz * 1.5)
        assertTrue("one gurdy note must not synthesize its own fifth", rootPower > accidentalFifth * 4.0)

        val bell = renderNote(Voice.BELLS, midi, 1.5f, 1f, sampleRate, kotlin.random.Random(9))
        val tunedPrime = tonePower(bell, sampleRate, rootHz)
        val oldFlatPrime = tonePower(bell, sampleRate, rootHz * 0.92)
        assertTrue("bell prime must be centered on requested MIDI", tunedPrime > oldFlatPrime * 2.0)
    }

    @Test
    fun thronePlanIsRegalProcessional() {
        val spec = resolveSongSpec(7L, emptyList(), throne = true)
        val plan = planOrchestration(spec, hasTrumpeter = false, rng = orchRng(7L))
        val l1 = activeAssignments(plan, 1).map { it.voice }
        // Melody, timpani, bells and the herald horn all sound from the first bar.
        assertTrue("melody leads from level 1: $l1", Voice.VIELLE in l1)
        assertTrue("timpani + bells from level 1", Voice.TIMPANI in l1 && Voice.BELLS in l1)
        assertTrue("herald horn fanfare from level 1", Voice.HORN in l1)
        // The choir lifts later rather than looming from the start.
        assertTrue("choir held back from level 1", Voice.CHOIR !in l1)
        assertTrue("choir joins the procession eventually", plan.assignments.any { it.voice == Voice.CHOIR })
        assertTrue("deep runs earn the coronation fanfare", plan.destinyFanfare)
    }

    @Test
    fun throneTimpaniIsSparseButEnormous() {
        val spec = resolveSongSpec(7L, emptyList(), throne = true)
        val events = percussionEvents(spec, Voice.TIMPANI, wilder = false)
        val downbeats = events.filter { it.velocity >= 1f }
        assertEquals(spec.totalBars / 2, downbeats.size)
    }

    @Test
    fun throneComposeIsAudible() {
        val buf = ProceduralMedievalComposer.compose(7L, 1, false, 22050, emptyList(), throne = true)
        assertTrue(buf.any { it != 0.toShort() })
    }

    @Test
    fun drumsSpeakInThePhoneBand() {
        val sampleRate = 44100
        for (voice in listOf(Voice.NAKERS, Voice.TIMPANI, Voice.BODHRAN)) {
            val buf = renderNote(voice, 45, 0.5f, 1f, sampleRate, kotlin.random.Random(3))
            val mid = bandRms(buf, sampleRate, 300f, 2000f)
            val low = bandRms(buf, sampleRate, 20f, 300f)
            assertTrue("$voice mid band $mid must dominate sub-300 band $low", mid > low)
        }
        val tabor = renderNote(Voice.TABOR, 45, 0.5f, 1f, sampleRate, kotlin.random.Random(3))
        val taborSnare = bandRms(tabor, sampleRate, 500f, 4000f)
        val taborLow = bandRms(tabor, sampleRate, 20f, 300f)
        assertTrue(
            "tabor snare band $taborSnare must dominate low band $taborLow",
            taborSnare > taborLow
        )
        val tambourine = renderNote(Voice.TAMBOURINE, 45, 0.5f, 1f, sampleRate, kotlin.random.Random(3))
        val tambourinePhone = bandRms(tambourine, sampleRate, 700f, 4500f)
        val tambourineLow = bandRms(tambourine, sampleRate, 20f, 500f)
        assertTrue(
            "tambourine needs midrange frame/jingle energy for small speakers",
            tambourinePhone > tambourineLow
        )
    }

    @Test
    fun combatImpactsRetainPhoneBandBody() {
        for (type in listOf(SoundType.THWACK, SoundType.CRUNCH, SoundType.DRUM_ROLL)) {
            val pcm = SfxGenerator.generate(type, 22050, kotlin.random.Random(12))
            val signal = FloatArray(pcm.size) { pcm[it] / 32768f }
            val phone = bandRms(signal, 22050, 300f, 2000f)
            val low = bandRms(signal, 22050, 20f, 300f)
            assertTrue("$type phone body $phone is too weak beside lows $low", phone > low * 0.6)
        }
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

    private fun tonePower(x: FloatArray, sr: Int, hz: Double): Double {
        var real = 0.0
        var imaginary = 0.0
        for (i in x.indices) {
            val phase = 2.0 * Math.PI * hz * i / sr
            real += x[i] * Math.cos(phase)
            imaginary -= x[i] * Math.sin(phase)
        }
        return real * real + imaginary * imaginary
    }

    /** RMS of x after a 2nd-order highpass at lo and lowpass at hi (crude band meter). */
    private fun bandRms(x: FloatArray, sr: Int, lo: Float, hi: Float): Double {
        val hp = Biquad.highpass(sr, lo, 0.707f)
        val lp = Biquad.lowpass(sr, hi, 0.707f)
        var acc = 0.0
        for (v in x) {
            val y = lp.process(hp.process(v))
            acc += y * y
        }
        return Math.sqrt(acc / x.size)
    }
}
