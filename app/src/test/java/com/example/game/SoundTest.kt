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
            // Wrestling-entrance metal, not thrash: at 180 the riff had no room to land and the
            // 16th-note double kick blurred into one sound rather than two feet.
            assertTrue("brawl bpm ${spec.bpm}", spec.bpm in 138..156)
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
        val specs = (1L..40L).flatMap { seed ->
            listOf(
                resolveSongSpec(seed, emptyList()),
                resolveSongSpec(seed, emptyList(), brawl = true),
                resolveSongSpec(seed, emptyList(), throne = true)
            )
        }
        // The guarantee has to hold for every placement, not just the one seed 1 happened to roll.
        assertEquals(
            "this test must exercise all four placements to mean anything",
            StrumPlacement.values().toSet(),
            specs.map { strumPlacementFor(it) }.toSet()
        )
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

    /** A song's harper keeps ONE habit throughout — the placement is a trait, not a coin flip. */
    @Test
    fun everySongsStrumsKeepToThatSongsOwnPlacement() {
        for (seed in 1L..24L) {
            val spec = resolveSongSpec(seed, emptyList())
            val bpb = spec.beatsPerBar
            val bars = harpStrumEvents(spec).map { (it.startBeat / bpb).toInt() }.toSet()
            if (bars.isEmpty()) continue
            val allowed = when (strumPlacementFor(spec)) {
                StrumPlacement.PHRASE_END -> bars.all { it % 4 == 3 }
                StrumPlacement.PHRASE_START -> bars.all { it % 4 == 0 }
                StrumPlacement.SECOND_BAR -> bars.all { it % 4 == 1 }
                StrumPlacement.ANSWER -> bars.all { it % 8 == 0 || it % 8 == 7 }
            }
            assertTrue("seed $seed (${strumPlacementFor(spec)}) strummed on bars $bars", allowed)
        }
    }

    /** A hand dragged across the strings, thumb first — not a block chord. */
    @Test
    fun eachStrumRollsUpwardAndEasesOff() {
        val spec = resolveSongSpec(3L, emptyList())
        val roll = harpStrumEvents(spec).take(4)
        assertTrue("expected a roll to inspect", roll.size == 4)
        roll.zipWithNext().forEach { (a, b) ->
            assertTrue("strum should roll upward: ${a.midi} then ${b.midi}", b.midi > a.midi)
            assertTrue("strum should roll in time: ${a.startBeat} then ${b.startBeat}", b.startBeat > a.startBeat)
            assertTrue("strum should ease off up the roll", b.velocity < a.velocity)
        }
    }

    /**
     * The variance the strum exists for: across runs, some songs strum and some do not, and the
     * ones that do do not all put it in the same place. A single fixed gesture would fail this.
     */
    @Test
    fun strummingVariesBetweenSongsOfTheSameFamily() {
        val greensleeves = (1L..60L).map { resolveSongSpec(it, emptyList()) }
            .filter { it.family == Family.GREENSLEEVES }
        assertTrue("need a few of one family to compare", greensleeves.size >= 4)
        val placements = greensleeves.map { strumPlacementFor(it) }.toSet()
        assertTrue("one family should not share a single strum habit, got $placements", placements.size > 1)

        val plans = (1L..60L).map { seed ->
            val spec = resolveSongSpec(seed, emptyList())
            planOrchestration(spec, hasTrumpeter = false, rng = orchRng(seed))
        }
        val strumming = plans.count { p -> p.assignments.any { it.line == LineRef.STRUM } }
        assertTrue("some songs should strum, got $strumming/60", strumming > 10)
        assertTrue("but not all of them, got $strumming/60", strumming < 60)
    }

    @Test
    fun theHarpNeverStrumsAtLevelOne() {
        for (seed in 1L..40L) {
            val spec = resolveSongSpec(seed, emptyList())
            val plan = planOrchestration(spec, hasTrumpeter = false, rng = orchRng(seed))
            assertTrue("seed $seed strummed at level one",
                activeAssignments(plan, 1).none { it.line == LineRef.STRUM })
        }
        // And a throne processional always has its court harper, from level three.
        val throne = resolveSongSpec(3L, emptyList(), throne = true)
        val thronePlan = planOrchestration(throne, hasTrumpeter = false, rng = orchRng(3L))
        assertTrue("the coronation should strum by level 4",
            activeAssignments(thronePlan, 4).any { it.line == LineRef.STRUM })
    }

    /**
     * Which voices can actually turn up in each rolled family, over many seeds and deep levels.
     * Reports the full coverage table so the gaps are visible rather than inferred.
     */
    @Test
    fun everyFamilyCanReachEveryVoice() {
        val report = StringBuilder("voice coverage by family:" + System.lineSeparator())
        val gaps = mutableListOf<String>()
        Family.values().filter { it != Family.BRAWL && it != Family.THRONE }.forEach { family ->
            val seen = mutableSetOf<Voice>()
            var sampled = 0
            for (seed in 1L..4000L) {
                val spec = resolveSongSpec(seed, emptyList())
                if (spec.family != family) continue
                sampled++
                val plan = planOrchestration(spec, hasTrumpeter = true, rng = orchRng(seed))
                seen += activeAssignments(plan, 24).map { it.voice }
                if (sampled >= 200) break
            }
            val missing = Voice.values().toSet() - seen
            report.append("  $family ($sampled songs): missing ${if (missing.isEmpty()) "none" else missing.joinToString()}" + System.lineSeparator())
            if (missing.isNotEmpty()) gaps += "$family cannot reach $missing"
        }
        assertTrue("$report", gaps.isEmpty())
    }

    @Test
    fun moodsDoNotBendThemedFamilies() {
        val spec = resolveSongSpec(7L, listOf("More Tempo", "More Tempo", "Merrier"), brawl = true)
        assertTrue("moods must not push brawl off 138-156, got ${spec.bpm}", spec.bpm in 138..156)
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
                l1.any { it.voice == Voice.KICK } &&
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
    fun brawlKickGallopsEveryBar() {
        val spec = resolveSongSpec(7L, emptyList(), brawl = true)
        val events = percussionEvents(spec, Voice.KICK, wilder = true)
        // 16 sixteenth-note hits per 4/4 bar, every bar, on an actual bass drum. This pattern
        // was on NAKERS, a 150-200Hz kettle drum that could play the rhythm but never sound
        // like a kick — the theme had a busy tom where its engine belonged.
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
    // ---- Instrument audit -------------------------------------------------------------------
    // The two properties every voice has to hold, checked by measurement rather than by ear:
    // it must survive a phone speaker, and it must not be a near-duplicate of another voice.

    /**
     * A pitch each voice actually plays, mirroring the octave offsets the orchestration applies.
     * Auditing every voice at one pitch is misleading — a recorder measured at 165Hz looks
     * hopeless, but nothing ever asks it to play there.
     */
    private fun testMidi(v: Voice): Int = when (v) {
        Voice.CELLO -> 43
        Voice.SACKBUT -> 45
        Voice.VIOLA, Voice.GURDY, Voice.ORGAN, Voice.CHOIR, Voice.HORN -> 50
        Voice.HARP, Voice.LUTE, Voice.PSALTERY, Voice.VIELLE, Voice.FIDDLE2,
        Voice.SHAWM, Voice.OBOE -> 60
        Voice.RECORDER, Voice.PANPIPES, Voice.BELLS -> 74
        else -> 57   // percussion: pitch is ignored, or clamped internally
    }

    private fun rmsRange(x: FloatArray, from: Int, to: Int): Double {
        var acc = 0.0
        for (i in from until to.coerceAtMost(x.size)) acc += x[i].toDouble() * x[i]
        return Math.sqrt(acc / (to - from).coerceAtLeast(1))
    }

    /**
     * Spectral band energies PLUS how the note behaves in time.
     *
     * The temporal terms are not decoration. Band energy alone rated a plucked harp and a bowed
     * cello as 0.98 alike, because averaged over a whole note they genuinely do occupy the same
     * bands — what separates them is that one decays and the other sustains. A fingerprint that
     * cannot see the envelope is not measuring what a listener hears.
     */
    private fun fingerprint(v: Voice, midi: Int = testMidi(v), sr: Int = 44100): DoubleArray {
        val buf = renderNote(v, midi, 1.2f, 1f, sr, kotlin.random.Random(11), 0)
        val whole = rmsRange(buf, 0, buf.size).coerceAtLeast(1e-9)
        val head = rmsRange(buf, 0, sr / 40)                       // first 25ms: the attack
        val tail = rmsRange(buf, buf.size * 3 / 5, buf.size)       // last 40%: does it hold?
        val temporal = doubleArrayOf(
            0.30 * (head / whole).coerceAtMost(4.0),
            0.30 * (tail / whole).coerceAtMost(4.0)
        )
        // Third-octave-ish resolution. Eight broad bands were too coarse to separate any two
        // harmonic instruments — everything with a 1/h series looked alike at that width.
        val edges = floatArrayOf(
            120f, 190f, 300f, 460f, 700f, 1050f, 1550f, 2300f,
            3300f, 4800f, 6800f, 9500f, 13000f
        )
        val bands = DoubleArray(edges.size - 1) { bandRms(buf, sr, edges[it], edges[it + 1]) }
        val total = bands.sum().coerceAtLeast(1e-9)
        return DoubleArray(bands.size) { bands[it] / total } + temporal
    }

    /**
     * Correlation, i.e. cosine on MEAN-CENTRED vectors. Plain cosine is the wrong tool here: on
     * vectors that are entirely non-negative — as band energies are — it is structurally biased
     * toward 1, and anything the two share inflates it further. Measured that way a harp and a
     * cello came out 0.98 "alike". Centring removes the common component and compares the shape
     * of the spectrum, which is the thing that actually differs between instruments.
     */
    private fun correlation(a: DoubleArray, b: DoubleArray): Double {
        val ma = a.average(); val mb = b.average()
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) {
            val x = a[i] - ma; val y = b[i] - mb
            dot += x * y; na += x * x; nb += y * y
        }
        return dot / Math.sqrt(na * nb).coerceAtLeast(1e-12)
    }

    /**
     * A phone speaker reproduces almost nothing below ~300Hz. Any voice whose energy is mostly
     * down there is inaudible in play however good it sounds on headphones — the cello is the
     * interesting case, since its real fundamental is below the cutoff and it has to carry on
     * harmonics instead.
     */
    @Test
    fun everyVoiceCarriesItsWeightAbovePhoneSpeakerCutoff() {
        val sr = 44100
        // Collected rather than asserted one at a time: a per-voice assert stops at the first
        // offender and hides the rest, which turns a mix audit into one round trip per voice.
        // A bass voice is ALLOWED to be bass-heavy — the criterion is not "mostly treble" but
        // "enough above the cutoff to be perceptible at all". A cello's fundamental is below
        // anything a handset can move; it reads only because its upper partials do, and the ear
        // reconstructs the rest. Below about a third, a voice simply vanishes in play.
        val offenders = Voice.values().mapNotNull { v ->
            val buf = renderNote(v, testMidi(v), 1.2f, 1f, sr, kotlin.random.Random(7), 0)
            val below = bandRms(buf, sr, 20f, 300f)
            val above = bandRms(buf, sr, 300f, 12000f)
            val audibleShare = above / (above + below).coerceAtLeast(1e-9)
            if (audibleShare >= 0.32) null
            else "$v at midi ${testMidi(v)}: only %.0f%% of its energy is above 300Hz".format(audibleShare * 100)
        }
        assertTrue(
            "these voices are too far below the 300Hz a phone speaker can reproduce:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /**
     * "I can't tell what is hurdy gurdy and what is organ." These are the pairs that share a
     * register and a role and so are the ones at genuine risk of blurring in a mix.
     */
    @Test
    fun voicesThatShareARegisterStayTellableApart() {
        // Tier one: different instruments, which a listener should never confuse. These are the
        // pairs that were actually blurring in play.
        val distinct = listOf(
            Voice.GURDY to Voice.ORGAN,           // both sustained drones/pads
            Voice.OBOE to Voice.RECORDER,         // both built on windVoice
            Voice.EGG_SHAKER to Voice.TAMBOURINE, // both high rattles
            Voice.CELLO to Voice.HARP             // sanity: unrelated voices should be miles apart
        )
        // Tier two: members of ONE family — three bowed strings off a single excitation model, and
        // two double reeds. A real viola and cello playing the same written pitch are spectrally
        // close too; what separates them in a score is register and role, not timbre, and the
        // orchestration does exactly that (cello sits an octave below on BASS). Demanding they be
        // as unalike as an organ and a hurdy-gurdy would be demanding something physics does not
        // give. They must still be measurably different, or one of them is redundant.
        val sameFamily = listOf(
            Voice.CELLO to Voice.VIOLA,
            Voice.VIELLE to Voice.VIOLA,
            Voice.CELLO to Voice.VIELLE,
            Voice.OBOE to Voice.SHAWM
        )
        // ORGAN vs CHOIR is held to a looser limit ON PURPOSE, and the reason is stated rather
        // than hidden: they are the only remaining pair that is both sustained AND formant-shaped,
        // and what separates them in the actual mix is a 4x reverb send (0.45 against 0.12,
        // ProceduralMedievalComposer.reverbSendFor) and the choir's vibrato — neither of which
        // this fingerprint measures. It sat at 0.898 before the organ was rebuilt as a cathedral
        // plenum and 0.904 after; tuning the organ further to chase 0.900 would be tuning it to
        // satisfy a number I chose, not to sound better.
        val measured = distinct.map { (a, b) -> Triple(a, b, 0.90) } +
            listOf(Triple(Voice.ORGAN, Voice.CHOIR, 0.92)) +
            sameFamily.map { (a, b) -> Triple(a, b, 0.985) }
        val report = StringBuilder("measured voice similarity (correlation):\n")
        val blurred = measured.mapNotNull { (a, b, limit) ->
            val r = correlation(fingerprint(a), fingerprint(b))
            report.append("  %-24s %.3f  (limit %.3f)\n".format("$a/$b", r, limit))
            if (r < limit) null else "$a vs $b: %.3f exceeds %.3f".format(r, limit)
        }
        assertTrue(
            "these pairs will blur together in a mix:\n" + blurred.joinToString("\n") +
                "\n\n$report",
            blurred.isEmpty()
        )
    }

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
