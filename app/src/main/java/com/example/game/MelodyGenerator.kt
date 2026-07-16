package com.example.game

import kotlin.random.Random

data class NoteEvent(val startBeat: Float, val durBeats: Float, val midi: Int, val velocity: Float)
data class ChordEvent(val startBeat: Float, val durBeats: Float, val midis: List<Int>)
data class SongEvents(
    val melody: List<NoteEvent>,
    val melodyOrnamented: List<NoteEvent>,
    val counter: List<NoteEvent>,
    val bass: List<NoteEvent>,
    val padChords: List<ChordEvent>
)

private const val FICTA = Int.MIN_VALUE  // marker: leading tone (final - 1 semitone)

private fun rhythmPatterns(beatsPerBar: Int): List<List<Float>> = when (beatsPerBar) {
    6 -> listOf(  // 6/8, beat = quaver, strong {0,3}
        listOf(3f, 3f), listOf(2f, 1f, 3f), listOf(3f, 2f, 1f),
        listOf(1f, 1f, 1f, 3f), listOf(2f, 1f, 2f, 1f)
    )
    3 -> listOf(  // 3/4 minuet
        listOf(1f, 1f, 1f), listOf(1.5f, 0.5f, 1f), listOf(1f, 0.5f, 0.5f, 1f), listOf(2f, 1f)
    )
    else -> listOf( // 4/4 broad
        listOf(2f, 2f), listOf(2f, 1f, 1f), listOf(1f, 1f, 2f), listOf(3f, 1f), listOf(1f, 1f, 1f, 1f)
    )
}

private fun strongBeats(beatsPerBar: Int): Set<Float> = when (beatsPerBar) {
    6 -> setOf(0f, 3f); 3 -> setOf(0f); else -> setOf(0f, 2f)
}

// Melody register: keep degrees within [7, 16] approximately D4-A5 for finals 45-52.
private const val DEG_LO = 7
private const val DEG_HI = 16

private fun chordDegreesAt(spec: SongSpec, bar: Int): List<Int> {
    val g = spec.ground[bar % 8].bassDegree
    return listOf(g, g + 2, g + 4)
}

private fun nearestChordDegree(spec: SongSpec, bar: Int, target: Int): Int {
    val chord = chordDegreesAt(spec, bar)
    var best = target; var bestDist = Int.MAX_VALUE
    for (c in chord) for (oct in -1..3) {
        val d = c + 7 * oct
        if (d in DEG_LO..DEG_HI) {
            val dist = Math.abs(d - target)
            if (dist < bestDist) { bestDist = dist; best = d }
        }
    }
    return best
}

/** One 8-bar strain: antecedent (4 bars, half close) + consequent (4 bars, full close). */
private fun generateStrain(
    spec: SongSpec, rng: Random, startDegree: Int, isB: Boolean
): List<Pair<Float, Pair<Float, Int>>> {  // (barOffsetBeat, (dur, degreeOrFICTA))
    val bpb = spec.beatsPerBar.toFloat()
    val patterns = rhythmPatterns(spec.beatsPerBar)
    val motifRhythm = patterns[rng.nextInt(patterns.size)]
    val altRhythm = patterns[rng.nextInt(patterns.size)]
    val out = mutableListOf<Pair<Float, Pair<Float, Int>>>()

    // Contour targets per bar (phrase peak in bar 3 of each 4-bar phrase)
    val base = startDegree
    val peak = (base + if (isB) 4 else 3).coerceAtMost(DEG_HI)
    val targets = listOf(base, base + 1, peak, base + 1, base, base + 1, peak - 1, base)

    var prev = base
    for (bar in 0 until 8) {
        val rhythm = if (bar % 2 == 0) motifRhythm else altRhythm  // rhythmic rhyme
        val barStart = bar * bpb
        val isCadBar = bar == 3 || bar == 7
        var beat = 0f
        val strong = strongBeats(spec.beatsPerBar)
        val noteCount = rhythm.size
        for ((i, dur) in rhythm.withIndex()) {
            val degree: Int
            if (isCadBar && i >= noteCount - 2) {
                // Cadence splice: last two notes of the cadence bar
                degree = if (bar == 3) {
                    if (i == noteCount - 2) 9 else 8            // half close: hang on degree 1 (9th = 2nd above octave-final 7... see note)
                } else {
                    // full close to the final's octave (degree 7)
                    when (cadenceFormula(spec, rng, bar)) {
                        0 -> if (i == noteCount - 2) 9 else 7   // stepwise 2-1 (upper)
                        1 -> if (i == noteCount - 2) FICTA else 7 // authentic with ficta leading tone
                        // Landini-ish 6-8 (7#-6-8 spread over graces later); the sixth degree
                        // sits below the register floor for low finals, so fall back to the
                        // ficta leading tone there (authentic close) rather than leave range.
                        else -> if (i == noteCount - 2) {
                            if (degreeToMidi(spec, 5) >= 56) 5 else FICTA
                        } else 7
                    }
                }
            } else if (bar == 0 && i <= 1 && spec.family == Family.TINTAGEL) {
                degree = if (i == 0) base else base + 3   // final, then the rising 4th/5th gesture
            } else if (strong.contains(beat)) {
                degree = nearestChordDegree(spec, bar, targets[bar] + rng.nextInt(-1, 2))
            } else {
                val toward = targets[bar]
                degree = (prev + Integer.signum(toward - prev).let { if (it == 0) rng.nextInt(-1, 2) else it })
                    .coerceIn(DEG_LO, DEG_HI)
            }
            out.add(barStart + beat to (dur to degree))
            if (degree != FICTA) prev = degree
            beat += dur
        }
    }
    return out
}

private fun cadenceFormula(spec: SongSpec, rng: Random, bar: Int): Int = rng.nextInt(3)

private fun toNotes(spec: SongSpec, raw: List<Pair<Float, Pair<Float, Int>>>, strainStartBeat: Float): List<NoteEvent> =
    raw.map { (beat, dn) ->
        val (dur, degree) = dn
        val midi = if (degree == FICTA) spec.finalMidi + 12 - 1 else degreeToMidi(spec, degree)
        val vel = if (strongBeats(spec.beatsPerBar).contains(beat % spec.beatsPerBar)) 1.0f else 0.85f
        NoteEvent(strainStartBeat + beat, dur, midi, vel)
    }

/** Divisions + graces for the repeat statement. */
private fun ornament(spec: SongSpec, notes: List<NoteEvent>, rng: Random): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val wildCount = spec.moods.count { it == "Wilder" }
    val density = spec.ornamentDensity * (1f + wildCount * 1.5f)
    for ((i, n) in notes.withIndex()) {
        val next = notes.getOrNull(i + 1)
        val divide = n.durBeats >= 2f && next != null && rng.nextFloat() < density
        val grace = n.durBeats >= 1f && rng.nextFloat() < density * 0.5f
        if (grace) {
            val g = 0.125f
            // Scale-aware upper neighbour - chromatic graces clash with sustained pads
            out.add(NoteEvent(n.startBeat, g, degreeToMidi(spec, nearestDegreeFor(spec, n.midi) + 1), n.velocity * 0.7f))
            if (divide) {
                val half = (n.durBeats - g) / 2f
                val step = degreeToMidi(spec, nearestDegreeFor(spec, n.midi) + Integer.signum(next.midi - n.midi))
                out.add(NoteEvent(n.startBeat + g, half, n.midi, n.velocity))
                out.add(NoteEvent(n.startBeat + g + half, half, step, n.velocity * 0.9f))
            } else {
                out.add(NoteEvent(n.startBeat + g, n.durBeats - g, n.midi, n.velocity))
            }
        } else if (divide) {
            val half = n.durBeats / 2f
            val step = degreeToMidi(spec, nearestDegreeFor(spec, n.midi) + Integer.signum(next.midi - n.midi))
            out.add(NoteEvent(n.startBeat, half, n.midi, n.velocity))
            out.add(NoteEvent(n.startBeat + half, half, step, n.velocity * 0.9f))
        } else out.add(n)
    }
    return out
}

/** Counter-voice: 3rds below (gymel), contrary-motion resolution at strain ends. */
private fun counterLine(spec: SongSpec, melody: List<NoteEvent>): List<NoteEvent> {
    val strainBeats = 8f * spec.beatsPerBar
    return melody.map { n ->
        val strainEnd = (Math.floor((n.startBeat / strainBeats).toDouble()) + 1) * strainBeats
        val isLast = n.startBeat + n.durBeats >= strainEnd - 1e-2f
        val midi = if (isLast) spec.finalMidi + modeOffset(spec.mode, 9) - 12  // mediant, resolving upward
                   else {
                       // nearest scale tone a 3rd-ish below
                       val deg = nearestDegreeFor(spec, n.midi) - 2
                       degreeToMidi(spec, deg)
                   }
        // Fold into register by octaves — a hard clamp landed on literal MIDI 52/80, which is
        // out of key for most finals and rang as a wrong-note drone under everything.
        var m = midi
        while (m < 52) m += 12
        while (m > 80) m -= 12
        NoteEvent(n.startBeat, n.durBeats, m, n.velocity * 0.8f)
    }
}

fun nearestDegreeFor(spec: SongSpec, midi: Int): Int {
    var best = 0; var bestDist = Int.MAX_VALUE
    for (d in 0..21) {
        val m = degreeToMidi(spec, d)
        val dist = Math.abs(m - midi)
        if (dist < bestDist) { bestDist = dist; best = d }
    }
    return best
}

/** Bass: the ground itself. One note per bar + optional passing quaver into the next bar. */
private fun bassLine(spec: SongSpec, rng: Random): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    for (bar in 0 until spec.totalBars) {
        val g = spec.ground[bar % 8].bassDegree
        val next = spec.ground[(bar + 1) % 8].bassDegree
        val midi = (spec.finalMidi + modeOffset(spec.mode, g)).let { if (it > 57) it - 12 else it }
        val passing = rng.nextFloat() < 0.35f && bar < spec.totalBars - 1
        if (passing) {
            val pDur = if (spec.beatsPerBar == 6) 1f else 0.5f
            out.add(NoteEvent(bar * bpb, bpb - pDur, midi, 0.9f))
            val pDeg = g + Integer.signum(next - g)
            val pMidi = (spec.finalMidi + modeOffset(spec.mode, pDeg)).let { if (it > 57) it - 12 else it }
            out.add(NoteEvent(bar * bpb + bpb - pDur, pDur, pMidi, 0.7f))
        } else {
            out.add(NoteEvent(bar * bpb, bpb, midi, 0.9f))
        }
    }
    return out
}

private fun padLine(spec: SongSpec): List<ChordEvent> {
    val bpb = spec.beatsPerBar.toFloat()
    return (0 until spec.totalBars).map { bar ->
        val g = spec.ground[bar % 8].bassDegree
        val root = spec.finalMidi + modeOffset(spec.mode, g)
        ChordEvent(bar * bpb, bpb, listOf(root, root + 7, root + 12))
    }
}

fun generateSong(spec: SongSpec, rng: Random): SongEvents {
    val bpb = spec.beatsPerBar.toFloat()
    val strainBeats = 8f * bpb
    val aRaw = generateStrain(spec, rng, startDegree = 7, isB = false)
    val bRaw = generateStrain(spec, rng, startDegree = 9, isB = true)

    val a = toNotes(spec, aRaw, 0f)
    val b = toNotes(spec, bRaw, 0f)
    fun shift(list: List<NoteEvent>, beats: Float) = list.map { it.copy(startBeat = it.startBeat + beats) }

    val melody: List<NoteEvent>
    val ornamented: List<NoteEvent>
    if (spec.totalBars == 32) {   // |:A:||:B:| -> A A' B B'
        val aOrn = ornament(spec, a, rng); val bOrn = ornament(spec, b, rng)
        melody = a + shift(a, strainBeats) + shift(b, 2 * strainBeats) + shift(b, 3 * strainBeats)
        ornamented = a + shift(aOrn, strainBeats) + shift(b, 2 * strainBeats) + shift(bOrn, 3 * strainBeats)
    } else {                      // TINTAGEL 16 bars: A B
        val aOrn = ornament(spec, a, rng); val bOrn = ornament(spec, b, rng)
        melody = a + shift(b, strainBeats)
        ornamented = aOrn + shift(bOrn, strainBeats)
    }

    return SongEvents(
        melody = melody,
        melodyOrnamented = ornamented,
        counter = counterLine(spec, melody),
        bass = bassLine(spec, rng),
        padChords = padLine(spec)
    )
}
