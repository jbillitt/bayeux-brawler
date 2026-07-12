package com.example.game

import kotlin.random.Random

enum class LineRef { MELODY, MELODY_ORN, COUNTER, BASS, PADS_FULL, PADS_ROOT, PADS_FIFTH, DRONE, PERC, SPARKLE, ACCOMP, TRUMPETER, DESTINY_FANFARE }

data class VoiceAssignment(
    val voice: Voice, val line: LineRef, val enterLevel: Int, val exitLevel: Int = 99,
    val gain: Float, val pan: Float, val octave: Int = 0, val transposeDegrees: Int = 0
)

data class OrchestrationPlan(val assignments: List<VoiceAssignment>, val destinyFanfare: Boolean)

fun duckFactor(activeCount: Int): Float = 1f / (1f + 0.06f * (activeCount - 1))

fun activeAssignments(plan: OrchestrationPlan, level: Int): List<VoiceAssignment> =
    plan.assignments.filter { level >= it.enterLevel && level < it.exitLevel }

private fun <T> weightedPick(rng: Random, options: List<Pair<T, Float>>): T {
    val total = options.sumOf { it.second.toDouble() }.toFloat()
    var roll = rng.nextFloat() * total
    for ((v, w) in options) { roll -= w; if (roll <= 0f) return v }
    return options.last().first
}

fun planOrchestration(spec: SongSpec, hasTrumpeter: Boolean, rng: Random): OrchestrationPlan {
    val a = mutableListOf<VoiceAssignment>()
    val used = mutableSetOf<Voice>()
    val moods = spec.moods
    val merrierCount = moods.count { it == "Merrier" }
    val solemnCount = moods.count { it == "More Solemn" }
    val wildCount = moods.count { it == "Wilder" }
    val noblerCount = moods.count { it == "Nobler" }

    val wilder = wildCount > 0
    val merrier = merrierCount > 0
    val nobler = noblerCount > 0
    val solemn = solemnCount > 0

    // Role gains (fixed budget - the mix bus is never normalised)
    val gMel = 0.50f; val gAcc = 0.20f; val gSecond = 0.28f; val gBass = 0.34f
    val gPerc1 = 0.30f * (1f - solemnCount * 0.15f).coerceAtLeast(0.1f)
    var gDrone = 0.16f * (1f + wildCount * 1.5f)
    val gThird = 0.22f; var gPads = 0.14f * (1f + noblerCount * 0.5f) * (1f + solemnCount * 0.5f)
    val gPerc2 = 0.18f * (1f - solemnCount * 0.15f).coerceAtLeast(0.1f)
    val gWaits = 0.26f; val gSparkle = 0.16f
    var gBells = 0.22f * (1f + solemnCount * 0.5f)
    val gTimp = 0.30f

    // L1 soloist: family-biased {harp, lute}; self-accompanies until the bass arrives at L3
    val soloist = weightedPick(rng, when (spec.family) {
        Family.GREENSLEEVES -> listOf(Voice.HARP to 0.7f, Voice.LUTE to 0.3f)
        Family.MINUET -> listOf(Voice.LUTE to 0.6f, Voice.HARP to 0.4f)
        Family.TINTAGEL -> listOf(Voice.HARP to 0.6f, Voice.LUTE to 0.4f)
    })
    used += soloist
    a += VoiceAssignment(soloist, LineRef.MELODY, 1, 3, gMel, 0f)
    a += VoiceAssignment(soloist, LineRef.MELODY_ORN, 3, 99, gMel, 0f)      // decorates once the bass frees its hands
    a += VoiceAssignment(soloist, LineRef.ACCOMP, 1, 3, gAcc, 0f)

    // L2 second voice
    val second = weightedPick(rng, when (spec.family) {
        Family.TINTAGEL -> listOf(Voice.VIELLE to 0.6f, Voice.RECORDER to 0.25f, Voice.PANPIPES to 0.15f)
        Family.MINUET -> listOf(Voice.RECORDER to 0.5f, Voice.VIELLE to 0.3f, Voice.PANPIPES to 0.2f)
        Family.GREENSLEEVES -> listOf(Voice.RECORDER to 0.4f, Voice.VIELLE to 0.4f, Voice.PANPIPES to 0.2f)
    })
    used += second
    a += VoiceAssignment(second, LineRef.COUNTER, 2, 99, gSecond, 0.35f)

    // L3 bass takes the ground
    val bassPool = listOf(Voice.LUTE to 0.4f, Voice.VIOLA to 0.35f, Voice.SACKBUT to 0.25f).filter { it.first !in used }
    val bass = weightedPick(rng, if (bassPool.isEmpty()) listOf(Voice.VIOLA to 1f) else bassPool)
    used += bass
    a += VoiceAssignment(bass, LineRef.BASS, 3, 99, gBass, -0.25f)

    // L4 percussion I
    val perc1 = weightedPick(rng, listOf(Voice.BODHRAN to 0.6f, Voice.TABOR to 0.4f))
    a += VoiceAssignment(perc1, LineRef.PERC, 4, 99, gPerc1, -0.5f)
    if (spec.family == Family.TINTAGEL) a += VoiceAssignment(Voice.NAKERS, LineRef.PERC, 4, 99, gPerc1 * 0.7f, 0.5f)

    // L5 drone bed
    val drone = weightedPick(rng, listOf(Voice.GURDY to 0.45f, Voice.ORGAN to 0.3f, Voice.VIOLA to 0.25f).filter { it.first != bass }
        .ifEmpty { listOf(Voice.GURDY to 1f) })
    a += VoiceAssignment(drone, LineRef.DRONE, 5, 99, gDrone, -0.7f)
    if (drone == Voice.GURDY) a += VoiceAssignment(Voice.GURDY, LineRef.SPARKLE, 5, 99, gDrone * 1.2f, -0.7f)
    // NOTE: gurdy buzz accents ride the SPARKLE slot pan; the facade maps GURDY+SPARKLE to gurdyBuzzEvents.

    // L6 third voice: divisions on the repeats
    val thirdPool = listOf(Voice.RECORDER, Voice.VIELLE, Voice.PANPIPES, Voice.PSALTERY).filter { it !in used }
    val third = thirdPool[rng.nextInt(thirdPool.size)]
    used += third
    val thirdOct = if (third == Voice.RECORDER || third == Voice.PANPIPES) 1 else 0
    a += VoiceAssignment(third, LineRef.MELODY_ORN, 6, 99, gThird, 0.6f, octave = thirdOct)

    // L7 pads: organ organum OR brass pair
    if (rng.nextBoolean()) {
        a += VoiceAssignment(Voice.ORGAN, LineRef.PADS_FULL, 7, 99, gPads, 0.15f)
    } else {
        a += VoiceAssignment(Voice.SACKBUT, LineRef.PADS_ROOT, 7, 99, gPads, -0.15f)
        a += VoiceAssignment(Voice.HORN, LineRef.PADS_FIFTH, 7, 99, gPads * 0.8f, 0.15f)
    }

    // L8 percussion II
    val tambLevel = (if (merrier) 6 else 8)
    a += VoiceAssignment(Voice.TAMBOURINE, LineRef.PERC, tambLevel, 99, gPerc2, 0.65f)

    // L9 waits band
    val shawmLevel = if (wilder) 7 else 9
    a += VoiceAssignment(Voice.SHAWM, LineRef.MELODY, shawmLevel, 99, gWaits, -0.35f)
    if (bass != Voice.SACKBUT) a += VoiceAssignment(Voice.SACKBUT, LineRef.BASS, 9, 99, gBass * 0.6f, -0.3f, octave = 0)

    // L10 bells + psaltery sparkle
    a += VoiceAssignment(Voice.BELLS, LineRef.SPARKLE, 10, 99, gBells, 0.8f)
    if (Voice.PSALTERY !in used) a += VoiceAssignment(Voice.PSALTERY, LineRef.SPARKLE, 10, 99, gSparkle, 0.45f)

    // L11 timpani
    val timpLevel = if (nobler) 10 else 11
    a += VoiceAssignment(Voice.TIMPANI, LineRef.PERC, timpLevel, 99, gTimp, 0f)

    // L12 destiny
    val destinyFanfare = rng.nextBoolean()
    if (destinyFanfare) {
        a += VoiceAssignment(Voice.HORN, LineRef.DESTINY_FANFARE, 12, 99, 0.30f, -0.2f)
        a += VoiceAssignment(Voice.SHAWM, LineRef.DESTINY_FANFARE, 12, 99, 0.24f, 0.2f)
        a += VoiceAssignment(Voice.BELLS, LineRef.DESTINY_FANFARE, 12, 99, 0.22f, 0.7f)
    } else {
        a += VoiceAssignment(Voice.PSALTERY, LineRef.SPARKLE, 12, 99, gSparkle * 1.5f, 0.5f)
        a += VoiceAssignment(Voice.RECORDER, LineRef.MELODY, 12, 99, 0.18f, 0.55f, octave = 1)
        a += VoiceAssignment(Voice.VIOLA, LineRef.DRONE, 12, 99, gDrone, -0.75f, octave = -1)
    }

    // Trumpeter ancillary cameo
    if (hasTrumpeter) a += VoiceAssignment(Voice.HORN, LineRef.TRUMPETER, 2, 99, 0.28f, 0.3f)

    // 13+ consort thickening: seconds and thirds of the family
    a += VoiceAssignment(Voice.RECORDER, LineRef.MELODY, 13, 99, 0.16f, 0.5f, transposeDegrees = -2)
    a += VoiceAssignment(Voice.FIDDLE2, LineRef.MELODY, 15, 99, 0.18f, -0.55f, transposeDegrees = -5)
    a += VoiceAssignment(Voice.VIOLA, LineRef.COUNTER, 17, 99, 0.16f, -0.4f, octave = -1)
    a += VoiceAssignment(soloist, LineRef.MELODY, 19, 99, 0.16f, 0.1f, octave = 1)
    a += VoiceAssignment(Voice.FIDDLE2, LineRef.DRONE, 21, 99, 0.10f, 0.75f, octave = 1)

    return OrchestrationPlan(a, destinyFanfare)
}

// ---- Pattern generators (all pure functions of the spec - level-free, rng-free) ----

fun percussionEvents(spec: SongSpec, voice: Voice, wilder: Boolean): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    for (bar in 0 until spec.totalBars) {
        val base = bar * bpb
        when (voice) {
            Voice.BODHRAN, Voice.TABOR -> {
                out += NoteEvent(base, 0.4f, 57, 1f)
                when (spec.beatsPerBar) {
                    6 -> { out += NoteEvent(base + 3f, 0.4f, 57, 0.7f); if (wilder) { out += NoteEvent(base + 4.5f, 0.3f, 57, 0.5f); out += NoteEvent(base + 5.5f, 0.3f, 57, 0.5f) } }
                    4 -> { out += NoteEvent(base + 2f, 0.4f, 57, 0.7f); if (wilder) { out += NoteEvent(base + 2.5f, 0.3f, 57, 0.5f); out += NoteEvent(base + 3.5f, 0.3f, 57, 0.5f) } }
                    else -> if (wilder) { out += NoteEvent(base + 1.5f, 0.3f, 57, 0.5f); out += NoteEvent(base + 2.5f, 0.3f, 57, 0.5f) }
                }
            }
            Voice.TAMBOURINE -> {
                val offs = when (spec.beatsPerBar) { 6 -> listOf(1.5f, 4.5f); 4 -> listOf(1f, 3f); else -> listOf(1f, 2f) }
                for (o in offs) out += NoteEvent(base + o, 0.25f, 57, 0.8f)
            }
            Voice.NAKERS -> if (bar % 4 == 0) { out += NoteEvent(base, 0.4f, 56, 0.9f); out += NoteEvent(base + 0.5f, 0.3f, 57, 0.6f) }
            Voice.TIMPANI -> {
                if (bar % 8 == 0) out += NoteEvent(base, 1.2f, spec.finalMidi, 1f)
                if (bar % 8 == 7) for (r in 0 until 6) out += NoteEvent(base + bpb - 1f + r * (1f / 6f), 0.15f, spec.finalMidi, 0.4f + 0.08f * r)
            }
            else -> {}
        }
    }
    return out
}

fun droneEvents(spec: SongSpec): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    var bar = 0
    while (bar < spec.totalBars) {
        val len = minOf(4, spec.totalBars - bar) * bpb
        out += NoteEvent(bar * bpb, len, spec.finalMidi, 0.9f)
        out += NoteEvent(bar * bpb, len, spec.finalMidi + 7, 0.7f)
        bar += 4
    }
    return out
}

fun gurdyBuzzEvents(spec: SongSpec): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    val strong: List<Float> = when (spec.beatsPerBar) { 6 -> listOf(0f, 3f); 4 -> listOf(0f, 2f); else -> listOf(0f) }
    for (bar in 0 until spec.totalBars) for (s in strong) out += NoteEvent(bar * bpb + s, 0.07f, spec.finalMidi, 0.9f)
    return out
}

fun sparkleEvents(spec: SongSpec): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    val step = if (spec.beatsPerBar == 6) 1f else 0.5f
    for (bar in 0 until spec.totalBars) {
        val root = spec.finalMidi + modeOffset(spec.mode, spec.ground[bar % 8].bassDegree) + 24
        val arp = intArrayOf(0, 7, 12, 7)
        var beat = 0f; var i = 0
        while (beat < bpb - 1e-3f) {
            out += NoteEvent(bar * bpb + beat, step * 1.5f, root + arp[i % 4], 0.7f)
            beat += step; i++
        }
    }
    return out
}

fun accompEvents(spec: SongSpec): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    for (bar in 0 until spec.totalBars) {
        val g = spec.ground[bar % 8].bassDegree
        val root = spec.finalMidi + modeOffset(spec.mode, g)
        out += NoteEvent(bar * bpb, bpb, root, 0.9f)                        // bass note, downbeat
        val mid = if (spec.beatsPerBar == 6) 3f else spec.beatsPerBar / 2f
        out += NoteEvent(bar * bpb + mid * 0.5f, 1f, root + 7, 0.55f)       // broken chord: fifth
        out += NoteEvent(bar * bpb + mid, 1f, root + 12, 0.5f)              // octave
    }
    return out
}

fun trumpeterEvents(spec: SongSpec): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    val strainBars = 8
    var bar = strainBars - 1
    while (bar < spec.totalBars) {
        val base = bar * bpb
        out += NoteEvent(base, 0.5f, spec.finalMidi + 12, 0.9f)
        out += NoteEvent(base + 0.5f, 0.5f, spec.finalMidi + 19, 0.9f)
        out += NoteEvent(base + 1f, 1f, spec.finalMidi + 24, 1f)
        bar += strainBars
    }
    return out
}

fun destinyFanfareEvents(spec: SongSpec): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    var bar = 7
    while (bar < spec.totalBars) {
        val base = bar * bpb
        val triad = intArrayOf(0, 4, 7, 12)
        for ((i, t) in triad.withIndex()) out += NoteEvent(base + i * 0.25f, 0.8f, spec.finalMidi + 12 + t, 0.95f)
        bar += 8
    }
    return out
}
