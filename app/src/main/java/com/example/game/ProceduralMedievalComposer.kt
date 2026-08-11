package com.example.game

import kotlin.random.Random

/**
 * Facade preserving the v1 API. NOTE: compose() now returns INTERLEAVED STEREO
 * (L,R,L,R...) at the given sample rate; MedievalHarpPlayer is its only consumer.
 * currentRootMidi still feeds MedievalAudioSynth (HUZZAH / VICTORY_FANFARE).
 */
object ProceduralMedievalComposer {
    var currentRootMidi: Float = 48f
    var currentThirdOffset: Float = 3f // minor third by default


    fun midiToFreq(midi: Float): Float = (440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)).toFloat()

    fun compose(seed: Long, level: Int, hasTrumpeter: Boolean, sampleRate: Int, moods: List<String> = emptyList(), brawl: Boolean = false, throne: Boolean = false): ShortArray {
        // Moods re-flavour the run's own theme — they must NOT reseed it. Folding moods into the
        // seed regenerated the melody from scratch, so picking a mood on the reward screen swapped
        // your song for an unrelated one. Everything random stays keyed to the run seed; `moods`
        // reaches the music only through resolveSongSpec (mode/tempo/key) and `wilder` below.
        val spec = resolveSongSpec(seed, moods, brawl, throne)
        currentRootMidi = spec.finalMidi.toFloat()
        currentThirdOffset = spec.mode.steps[2].toFloat()
        val song = generateSong(spec, melodyRng(seed))
        val plan = planOrchestration(spec, hasTrumpeter, orchRng(seed))
        val active = activeAssignments(plan, level)
        val duck = duckFactor(active.size)
        val totalSamples = (spec.totalBars * spec.beatsPerBar * spec.secondsPerBeat * sampleRate).toInt()
        val bus = MixBus(sampleRate, totalSamples)
        val hrng = humaniseRng(seed)
        // One harper, one instrument, for the whole piece. Skewed toward the harp end: most runs
        // should sound like a harp, with the occasional gittern-strung one for variety — the
        // square keeps the middle of the range rare so it lands as one or the other.
        val harpTimbre = kotlin.random.Random(seed xor 0x2B9A17C5L).nextFloat().let { it * it }
        // Brawl keeps the wilder double-hit drum floor; it's a family now, not a mood.
        val wilder = "Wilder" in moods || spec.family == Family.BRAWL

        for (assign in active) {
            when (assign.line) {
                LineRef.PADS_FULL -> renderChords(bus, spec, assign, song.padChords, sampleRate, duck, hrng, harpTimbre)
                else -> {
                    val events = lineEvents(spec, song, assign, wilder)
                    renderEvents(bus, spec, assign, events, sampleRate, duck, hrng, harpTimbre)
                }
            }
        }
        return bus.masterToStereoPcm(loopAware = true, rng = hrng)
    }

    private fun lineEvents(spec: SongSpec, song: SongEvents, a: VoiceAssignment, wilder: Boolean): List<NoteEvent> {
        val base = when (a.line) {
            LineRef.MELODY -> song.melody
            LineRef.MELODY_ORN -> song.melodyOrnamented
            LineRef.COUNTER -> song.counter
            LineRef.BASS -> song.bass
            LineRef.PADS_ROOT -> if (spec.family == Family.BRAWL) brawlChordStabEvents(spec, fifth = false)
                else song.padChords.map { NoteEvent(it.startBeat, it.durBeats, it.midis[0], 0.8f) }
            LineRef.PADS_FIFTH -> if (spec.family == Family.BRAWL) brawlChordStabEvents(spec, fifth = true)
                else song.padChords.map { NoteEvent(it.startBeat, it.durBeats, it.midis[1], 0.8f) }
            LineRef.DRONE -> droneEvents(spec)
            LineRef.PERC -> percussionEvents(spec, a.voice, wilder)
            LineRef.PERC_LIGHT -> lightPercussionEvents(spec, a.voice, wilder)
            LineRef.SPARKLE -> if (a.voice == Voice.GURDY) gurdyBuzzEvents(spec)
                                else if (a.voice == Voice.BELLS) bellCadenceEvents(spec)
                                else sparkleEvents(spec)
            LineRef.ACCOMP -> accompEvents(spec)
            LineRef.RIFF -> brawlRiffEvents(spec)
            LineRef.FLOURISH -> brawlFlourishEvents(spec, a.voice)
            LineRef.STRUM -> harpStrumEvents(spec)
            LineRef.TRUMPETER -> trumpeterEvents(spec)
            LineRef.DESTINY_FANFARE -> destinyFanfareEvents(spec)
            LineRef.PADS_FULL -> emptyList()
        }
        // Strain hand-off: a voice with a parity only sounds on its own half of the strains, so a
        // paired voice on the same line picks the tune up where this one drops it.
        val scoped = if (a.strainParity < 0) base else {
            val strainBeats = 8f * spec.beatsPerBar
            base.filter { (it.startBeat / strainBeats).toInt() % 2 == a.strainParity }
        }
        if (a.octave == 0 && a.transposeDegrees == 0) return scoped
        return scoped.map { n ->
            val midi = if (a.transposeDegrees != 0)
                degreeToMidi(spec, nearestDegreeFor(spec, n.midi) + a.transposeDegrees) + 12 * a.octave
            else n.midi + 12 * a.octave
            n.copy(midi = midi)
        }
    }

    private fun bellCadenceEvents(spec: SongSpec): List<NoteEvent> {
        val bpb = spec.beatsPerBar.toFloat()
        return (1..spec.totalBars / 8).map { s ->
            NoteEvent(s * 8 * bpb - 1f, 2.5f, spec.finalMidi + 12, 0.9f)
        }
    }

    private fun renderEvents(bus: MixBus, spec: SongSpec, a: VoiceAssignment, events: List<NoteEvent>, sr: Int, duck: Float, hrng: Random, harpTimbre: Float) {
        val isPerc = a.line == LineRef.PERC
        // Phone speakers reproduce almost nothing below ~400Hz, so a drum's fundamental vanishes
        // and only its brief transient is left competing against held voices that sustain their
        // full level. Percussion therefore runs a flat mix boost — on-paper "correct" gains are
        // exactly why every previous pass left the drums inaudible on a handset.
        // CALIBRATION KNOB — tuned by ear on a handset, not derived. 1.8 was still inaudible in
        // play; raise this first if the drums go quiet again, before touching per-voice gains.
        val percBoost = if (isPerc) 2.7f else 1f
        for (e in events) {
            val jitterMs = if (isPerc) 1f else 3f
            val offs = ((e.startBeat * spec.secondsPerBeat) * sr).toInt() + ((hrng.nextFloat() * 2f - 1f) * jitterMs / 1000f * sr).toInt()
            val durSec = (e.durBeats * spec.secondsPerBeat).coerceAtLeast(0.05f)
            val vel = (e.velocity * (1f + (hrng.nextFloat() * 2f - 1f) * 0.1f)).coerceIn(0.2f, 1f)
            // Tails are wall-clock; at wound-up tempos a 0.8s harp ring spans the next chord and
            // the harmony smears. Scale them down with the beat so releases die before the change.
            val tail = releaseTail(a.voice) * (spec.secondsPerBeat * 2f).coerceIn(0.35f, 1f)
            val phraseIndex = (e.startBeat / (spec.beatsPerBar * 4f)).toInt()
            val note = renderNote(a.voice, e.midi, durSec + tail, vel, sr, hrng, phraseIndex, harpTimbre)
            bus.add(note, offs, a.gain * duck * percBoost, a.pan, reverbSendFor(a.voice))
        }
    }

    private fun renderChords(bus: MixBus, spec: SongSpec, a: VoiceAssignment, chords: List<ChordEvent>, sr: Int, duck: Float, hrng: Random, harpTimbre: Float) {
        for (c in chords) {
            val offs = ((c.startBeat * spec.secondsPerBeat) * sr).toInt()
            val durSec = c.durBeats * spec.secondsPerBeat
            val phraseIndex = (c.startBeat / (spec.beatsPerBar * 4f)).toInt()
            for (m in c.midis) {
                val note = renderNote(a.voice, m, durSec + 0.05f, 0.8f, sr, hrng, phraseIndex, harpTimbre)
                bus.add(note, offs, a.gain * duck / c.midis.size * 2f, a.pan, reverbSendFor(a.voice))
            }
        }
    }

    private fun releaseTail(v: Voice): Float = when (v) {
        Voice.HARP, Voice.PSALTERY -> 0.8f
        Voice.LUTE -> 0.4f
        Voice.BELLS -> 1.5f
        // A kettledrum's whole character is the long tuned boom AFTER the stick leaves the head —
        // membrane() gives TIMPANI a 1.1s decay and it was being handed the general 0.08s drum
        // tail, so 90% of the note was cut off and what came out was a tuned tap. This is the
        // difference between a timpani and a tom.
        Voice.TIMPANI -> 1.2f
        Voice.NAKERS -> 0.25f
        Voice.GURDY -> 0f   // buzz/drone dispatch keys on durSec < 0.15s; a tail pushed slow-tempo buzzes over it
        else -> 0.08f
    }

    private fun reverbSendFor(v: Voice): Float = when (v) {
        // The cathedral is half the organ: a plenum in a stone building is as much room as pipe,
        // and this send is what places it there rather than in the same small hall as everyone else.
        Voice.ORGAN -> 0.45f
        Voice.BELLS -> 0.25f
        // Percussion stays dry or the transients smear — and the shaker's whole job is definition.
        Voice.BODHRAN, Voice.TABOR, Voice.TAMBOURINE, Voice.NAKERS -> 0.08f
        Voice.KICK -> 0.03f   // a kick with a tail on it is mud
        Voice.EGG_SHAKER -> 0.05f
        else -> 0.12f
    }
}
