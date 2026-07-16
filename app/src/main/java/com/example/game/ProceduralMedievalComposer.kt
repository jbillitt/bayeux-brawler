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

    fun compose(seed: Long, level: Int, hasTrumpeter: Boolean, sampleRate: Int, moods: List<String> = emptyList()): ShortArray {
        // Moods re-flavour the run's own theme — they must NOT reseed it. Folding moods into the
        // seed regenerated the melody from scratch, so picking a mood on the reward screen swapped
        // your song for an unrelated one. Everything random stays keyed to the run seed; `moods`
        // reaches the music only through resolveSongSpec (mode/tempo/key) and `wilder` below.
        val spec = resolveSongSpec(seed, moods)
        currentRootMidi = spec.finalMidi.toFloat()
        currentThirdOffset = spec.mode.steps[2].toFloat()
        val song = generateSong(spec, melodyRng(seed))
        val plan = planOrchestration(spec, hasTrumpeter, orchRng(seed))
        val active = activeAssignments(plan, level)
        val duck = duckFactor(active.size)
        val totalSamples = (spec.totalBars * spec.beatsPerBar * spec.secondsPerBeat * sampleRate).toInt()
        val bus = MixBus(sampleRate, totalSamples)
        val hrng = humaniseRng(seed)
        val wilder = "Wilder" in moods || "Brawl" in moods

        for (assign in active) {
            when (assign.line) {
                LineRef.PADS_FULL -> renderChords(bus, spec, assign, song.padChords, sampleRate, duck, hrng)
                else -> {
                    val events = lineEvents(spec, song, assign, wilder)
                    renderEvents(bus, spec, assign, events, sampleRate, duck, hrng)
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
            LineRef.PADS_ROOT -> song.padChords.map { NoteEvent(it.startBeat, it.durBeats, it.midis[0], 0.8f) }
            LineRef.PADS_FIFTH -> song.padChords.map { NoteEvent(it.startBeat, it.durBeats, it.midis[1], 0.8f) }
            LineRef.DRONE -> droneEvents(spec)
            LineRef.PERC -> percussionEvents(spec, a.voice, wilder)
            LineRef.SPARKLE -> if (a.voice == Voice.GURDY) gurdyBuzzEvents(spec)
                                else if (a.voice == Voice.BELLS) bellCadenceEvents(spec)
                                else sparkleEvents(spec)
            LineRef.ACCOMP -> accompEvents(spec)
            LineRef.TRUMPETER -> trumpeterEvents(spec)
            LineRef.DESTINY_FANFARE -> destinyFanfareEvents(spec)
            LineRef.PADS_FULL -> emptyList()
        }
        if (a.octave == 0 && a.transposeDegrees == 0) return base
        return base.map { n ->
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

    private fun renderEvents(bus: MixBus, spec: SongSpec, a: VoiceAssignment, events: List<NoteEvent>, sr: Int, duck: Float, hrng: Random) {
        val isPerc = a.line == LineRef.PERC
        for (e in events) {
            val jitterMs = if (isPerc) 1f else 3f
            val offs = ((e.startBeat * spec.secondsPerBeat) * sr).toInt() + ((hrng.nextFloat() * 2f - 1f) * jitterMs / 1000f * sr).toInt()
            val durSec = (e.durBeats * spec.secondsPerBeat).coerceAtLeast(0.05f)
            val vel = (e.velocity * (1f + (hrng.nextFloat() * 2f - 1f) * 0.1f)).coerceIn(0.2f, 1f)
            // Tails are wall-clock; at wound-up tempos a 0.8s harp ring spans the next chord and
            // the harmony smears. Scale them down with the beat so releases die before the change.
            val tail = releaseTail(a.voice) * (spec.secondsPerBeat * 2f).coerceIn(0.35f, 1f)
            val note = renderNote(a.voice, e.midi, durSec + tail, vel, sr, hrng)
            bus.add(note, offs, a.gain * duck, a.pan, reverbSendFor(a.voice))
        }
    }

    private fun renderChords(bus: MixBus, spec: SongSpec, a: VoiceAssignment, chords: List<ChordEvent>, sr: Int, duck: Float, hrng: Random) {
        for (c in chords) {
            val offs = ((c.startBeat * spec.secondsPerBeat) * sr).toInt()
            val durSec = c.durBeats * spec.secondsPerBeat
            for (m in c.midis) {
                val note = renderNote(a.voice, m, durSec + 0.05f, 0.8f, sr, hrng)
                bus.add(note, offs, a.gain * duck / c.midis.size * 2f, a.pan, reverbSendFor(a.voice))
            }
        }
    }

    private fun releaseTail(v: Voice): Float = when (v) {
        Voice.HARP, Voice.PSALTERY -> 0.8f
        Voice.LUTE -> 0.4f
        Voice.BELLS -> 1.5f
        Voice.GURDY -> 0f   // buzz/drone dispatch keys on durSec < 0.15s; a tail pushed slow-tempo buzzes over it
        else -> 0.08f
    }

    private fun reverbSendFor(v: Voice): Float = when (v) {
        Voice.BELLS, Voice.ORGAN -> 0.25f
        Voice.BODHRAN, Voice.TABOR, Voice.TAMBOURINE, Voice.NAKERS -> 0.08f
        else -> 0.12f
    }
}
