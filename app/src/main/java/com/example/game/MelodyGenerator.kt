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

private fun rhythmPatterns(spec: SongSpec): List<List<Float>> {
    if (spec.family == Family.BRAWL) {
        return listOf(
            List(8) { 0.5f },
            listOf(0.5f, 0.5f, 0.5f, 0.5f, 1f, 1f),
            listOf(1f, 0.5f, 0.5f, 1f, 0.5f, 0.5f),
            listOf(0.5f, 1f, 0.5f, 0.5f, 1f, 0.5f)
        )
    }
    // Doublets and triplets against the prevailing beat, and the odd run. Without them a bar can
    // only ever be a handful of shapes, which is why one tune ends up sounding like the last one
    // however different its mode and ground are. A triplet in 4/4 is three notes in the time of
    // two; a doublet in 6/8 is two in the time of three — each is the borrowed division of the
    // other metre, and they are the cheapest thing that makes a phrase sound composed.
    //
    // But NOT in every song, or the borrowed divisions become the new sameness. Roughly two songs
    // in five have this vocabulary at all; the rest keep the plain metre and are told apart by it.
    val borrowed = usesBorrowedDivisions(spec)
    return when (spec.beatsPerBar) {
        6 -> listOf(  // 6/8, beat = quaver, strong {0,3}
            listOf(3f, 3f), listOf(2f, 1f, 3f), listOf(3f, 2f, 1f),
            listOf(1f, 1f, 1f, 3f), listOf(2f, 1f, 2f, 1f)
        ) + if (!borrowed) emptyList() else listOf(
            listOf(1.5f, 1.5f, 3f),                       // doublet, then the compound beat
            listOf(3f, 1.5f, 1.5f),                       // ...and the other way round
            listOf(1f, 1f, 1f, 1f, 1f, 1f),               // a run right through the bar
            listOf(0.5f, 0.5f, 0.5f, 0.5f, 1f, 3f)        // four quick, then broad
        )
        3 -> listOf(  // 3/4 minuet
            listOf(1f, 1f, 1f), listOf(1.5f, 0.5f, 1f), listOf(1f, 0.5f, 0.5f, 1f), listOf(2f, 1f)
        ) + if (!borrowed) emptyList() else listOf(
            listOf(1f / 3f, 1f / 3f, 1f / 3f, 1f, 1f),    // triplet on the first beat
            listOf(1f, 1f / 3f, 1f / 3f, 1f / 3f, 1f),    // and on the second
            listOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f)    // a run of six
        )
        else -> listOf( // 4/4 broad
            listOf(2f, 2f), listOf(2f, 1f, 1f), listOf(1f, 1f, 2f), listOf(3f, 1f), listOf(1f, 1f, 1f, 1f)
        ) + if (!borrowed) emptyList() else listOf(
            listOf(2f / 3f, 2f / 3f, 2f / 3f, 1f, 1f),    // triplet across the first two beats
            listOf(1f, 1f, 2f / 3f, 2f / 3f, 2f / 3f),    // ...and across the last two
            listOf(0.5f, 0.5f, 0.5f, 0.5f, 2f),           // a run of four into a long note
            listOf(2f, 0.5f, 0.5f, 0.5f, 0.5f),           // long note, then the run out of it
            listOf(1.5f, 0.5f, 1.5f, 0.5f)                // dotted, twice — a limping gait
        )
    }
}

/**
 * Does this song borrow the other metre's divisions — triplets in a duple bar, doublets in a
 * compound one — and take the quick runs?
 *
 * Per song, off the seed, so it is a trait of the piece rather than something that happens to
 * every piece. Applying a new vocabulary to all of them only moves where the sameness lives.
 */
private fun usesBorrowedDivisions(spec: SongSpec): Boolean =
    Random(spec.seed xor 0x3B9ACA07L).nextFloat() < 0.42f

/**
 * How readily this song's harper breaks a long note into a stepwise run. Two songs in five never
 * do it at all; the rest range from occasional to habitual. Same reasoning as above — the point is
 * that runs are a thing SOME tunes do.
 */
private fun runAppetite(spec: SongSpec): Float {
    val r = Random(spec.seed xor 0x1F123BB5L)
    return if (r.nextFloat() < 0.4f) 0f else 0.25f + r.nextFloat() * 0.45f
}

private fun strongBeats(beatsPerBar: Int): Set<Float> = when (beatsPerBar) {
    6 -> setOf(0f, 3f); 3 -> setOf(0f); else -> setOf(0f, 2f)
}

/**
 * Is this offset a strong beat? Compared with a tolerance rather than by equality, because a bar
 * containing a triplet arrives at its second strong beat via 2/3 + 2/3 + 2/3 and lands on
 * 2.0000002 — which is not 2f, so the whole downbeat would quietly stop being a downbeat.
 */
private fun isStrong(beatsPerBar: Int, beat: Float): Boolean =
    strongBeats(beatsPerBar).any { kotlin.math.abs(it - beat) < 0.02f }

// Melody register. The floor used to be degree 7 — the octave above the final — which is also
// where every phrase is anchored, so the tune sat on its own floor and could never dip below the
// tonic. That costs the lower neighbour, the commonest ornament there is, and it means a motif
// whose shape goes down has nowhere to go. A fifth of headroom underneath fixes both.
private const val DEG_LO = 5
private const val DEG_HI = 17

/**
 * The intervals a line may move by, in scale degrees.
 *
 * Drawn from a first-order chain — each interval's odds depend on the one before it — which puts
 * two rules of counterpoint older than the tapestry into one table: a melody moves by step far
 * more often than it leaps, and a leap is answered by a step back into the gap it opened. The
 * motif is built out of the same chain, so voice-leading and melodic shape come from one place
 * rather than two disagreeing ones.
 */
private val INTERVALS = intArrayOf(-4, -3, -2, -1, 0, 1, 2, 3, 4)

private fun nextInterval(rng: Random, prev: Int, tight: Boolean = false): Int {
    val recovering = Math.abs(prev) >= 3
    val w = FloatArray(INTERVALS.size) { i ->
        val iv = INTERVALS[i]
        val size = Math.abs(iv)
        // [tight] drops fourths and fifths from 12.5% of the chain to 2.5%. A motif is restated in
        // every bar of its strain, so at eight notes to the bar one wide leap is heard eight times.
        val base = if (tight) when (size) { 0 -> 3f; 1 -> 26f; 2 -> 4f; else -> 0.4f }
            else when (size) { 0 -> 4f; 1 -> 14f; 2 -> 5f; else -> 1.5f }
        if (!recovering) base else base * when {
            iv == 0 -> 0.5f
            size <= 2 && Integer.signum(iv) != Integer.signum(prev) -> 5f  // the step back in
            else -> 0.15f
        }
    }
    var roll = rng.nextFloat() * w.sum()
    for (i in INTERVALS.indices) { roll -= w[i]; if (roll <= 0f) return INTERVALS[i] }
    return 0
}

/** The germ every bar of a strain is made of: [len] intervals off the chain. */
private fun makeMotif(rng: Random, len: Int, tight: Boolean = false): List<Int> {
    var prev = 0
    return List(len) { nextInterval(rng, prev, tight).also { prev = it } }
}

/** State it, invert it, run it backwards, or both — the four permutations a strain works with. */
private fun varyMotif(motif: List<Int>, op: Int): List<Int> = when (op) {
    1 -> motif.map { -it }
    2 -> motif.reversed()
    3 -> motif.reversed().map { -it }
    else -> motif
}

/** A tritone against the bar's ground — the one interval the grounds and strums already dodge. */
private fun isTritoneOver(spec: SongSpec, bar: Int, degree: Int): Boolean {
    val root = degreeToMidi(spec, spec.ground[bar % 8].bassDegree)
    return Math.floorMod(degreeToMidi(spec, degree) - root, 12) == 6
}

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
    val patterns = rhythmPatterns(spec)
    val motifRhythm = patterns[rng.nextInt(patterns.size)]
    val altRhythm = patterns[rng.nextInt(patterns.size)]
    val out = mutableListOf<Pair<Float, Pair<Float, Int>>>()

    // Contour targets per bar (phrase peak in bar 3 of each 4-bar phrase)
    val base = startDegree
    val peak = (base + if (isB) 4 else 3).coerceAtMost(DEG_HI)
    val targets = listOf(base, base + 1, peak, base + 1, base, base + 1, peak - 1, base)

    // One motif, permuted, rather than a fresh contour every bar. Bars used to draw their pitches
    // from a random walk toward the target, so the tune rhymed rhythmically and never melodically —
    // eight bars of unrelated shapes over the same rhythm. Now every bar states the same intervals,
    // re-anchored to that bar's chord, and two bars per strain get the shape inverted or reversed.
    // That is the whole difference between a tune with an idea in it and a plausible note sequence.
    // A named piece states its own opening phrase; everything else invents one. Because every bar
    // of the strain is a permutation of this motif, handing it a real incipit makes the whole
    // strain a working-out of that phrase — the song is recognisably OF the piece rather than
    // merely labelled with its name. The A strain gets the incipit; B invents, so the middle of
    // the song still goes somewhere of its own.
    val motifLen = maxOf(motifRhythm.size, altRhythm.size, 2) - 1
    // BRAWL is the only family running eight notes to the bar at ~200bpm; at that rate a leapy germ
    // reads as scattered rather than as a tune, so its chain stays stepwise.
    val tight = spec.family == Family.BRAWL
    val motif = spec.piece?.incipit?.takeIf { !isB }?.let { incipit ->
        if (incipit.size >= motifLen) incipit.take(motifLen)
        else incipit + makeMotif(rng, motifLen - incipit.size, tight)
    } ?: makeMotif(rng, motifLen, tight)
    val ops = List(8) { bar -> if (bar == 2 || bar == 6) 1 + rng.nextInt(3) else 0 }

    var prev = base
    var barAnchor = base
    for (bar in 0 until 8) {
        val rhythm = if (bar % 2 == 0) motifRhythm else altRhythm  // rhythmic rhyme
        val barMotif = varyMotif(motif, ops[bar])
        val barStart = bar * bpb
        val isCadBar = bar == 3 || bar == 7
        var beat = 0f
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
            } else if (i == 0) {
                // The motif is transposed, not regenerated: each bar restates it from a chord tone
                // near that bar's point on the arch, which is what makes bar 5 hear as bar 1 again.
                degree = nearestChordDegree(spec, bar, targets[bar])
                barAnchor = degree
            } else {
                val delta = barMotif[(i - 1) % barMotif.size]
                // Off the top or bottom of the singer's range, the motif turns round rather than
                // being clamped flat against the wall — a clamp would erase the shape it is here
                // to preserve, and a run of repeated ceiling notes is exactly the old sameness.
                var d = prev + delta
                if (d !in DEG_LO..DEG_HI) d = prev - delta
                // Eight notes to the bar gives the walk room to drift a long way from the arch, and
                // the next downbeat then hauls it back in one leap — 70% of BRAWL's wide jumps were
                // that seam, not the motif. Turn the walk round near the edge of its own bar's
                // ambit, by the same reflection the register floor uses rather than a flat clamp.
                if (tight && Math.abs(d - barAnchor) > 3) d = prev - delta
                d = d.coerceIn(DEG_LO, DEG_HI)
                degree = if (isTritoneOver(spec, bar, d)) nearestChordDegree(spec, bar, d) else d
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
        // Velocity ceiling by what the song is FOR. A brawl leans on the beat; a coronation is
        // played by someone being paid to be tasteful. Same shape, different weight of hand.
        val ceiling = when (spec.family) {
            Family.BRAWL -> 1.0f
            Family.THRONE -> 0.72f
            else -> 0.9f
        }
        val vel = if (isStrong(spec.beatsPerBar, beat % spec.beatsPerBar)) ceiling else ceiling * 0.82f
        NoteEvent(strainStartBeat + beat, dur, midi, vel)
    }

/** Divisions + graces for the repeat statement. */
private fun ornament(spec: SongSpec, notes: List<NoteEvent>, rng: Random): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val wildCount = spec.moods.count { it == "Wilder" }.coerceAtMost(2)
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
            // A run rather than a plain division, some of the time: four stepwise notes walking
            // toward wherever the next note is. Divisions in two are what the piece already did to
            // every long note, and doing only that is a large part of why the tunes ran together.
            // Stepwise by construction — no interval wider than a second inside a run.
            val gapDegrees = nearestDegreeFor(spec, next.midi) - nearestDegreeFor(spec, n.midi)
            if (rng.nextFloat() < runAppetite(spec) && kotlin.math.abs(gapDegrees) >= 2) {
                val stepDir = Integer.signum(gapDegrees)
                val startDeg = nearestDegreeFor(spec, n.midi)
                val each = n.durBeats / 4f
                for (k in 0 until 4) {
                    out.add(NoteEvent(
                        n.startBeat + k * each, each,
                        degreeToMidi(spec, startDeg + stepDir * k),
                        n.velocity * (1f - 0.06f * k)
                    ))
                }
            } else {
                val half = n.durBeats / 2f
                val step = degreeToMidi(spec, nearestDegreeFor(spec, n.midi) + Integer.signum(next.midi - n.midi))
                out.add(NoteEvent(n.startBeat, half, n.midi, n.velocity))
                out.add(NoteEvent(n.startBeat + half, half, step, n.velocity * 0.9f))
            }
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
        ChordEvent(bar * bpb, bpb, groundChordMidis(spec, g))
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
    if (spec.totalBars == 32) {
        val aOrn = ornament(spec, a, rng); val bOrn = ornament(spec, b, rng)
        // Which of the four strains is which. The old form was fixed at A A' B B', so the tune
        // walked away from its own opening and never came back — every run had the same shape and
        // the B section read as "the song changed" rather than as a middle. These are all real
        // period forms; picking one per run is most of what stops the songs sounding alike.
        val form = when (rng.nextInt(3)) {
            // Verse, varied verse, a different middle, and home again — the one that most sounds
            // like a song with a chorus in it.
            0 -> listOf(a, aOrn, b, aOrn)
            // Alternating, the old estampie feel.
            1 -> listOf(a, b, aOrn, bOrn)
            // The original: both strains stated and repeated.
            else -> listOf(a, aOrn, b, bOrn)
        }
        melody = form.mapIndexed { i, strain -> shift(strain, i * strainBeats) }.flatten()
        // The ornamented voice takes the same FORM but decorates the repeats, so the two lines
        // never disagree about where the song is.
        ornamented = form.mapIndexed { i, strain ->
            shift(if (i % 2 == 1) ornament(spec, strain, rng) else strain, i * strainBeats)
        }.flatten()
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
