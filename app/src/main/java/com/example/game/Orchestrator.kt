package com.example.game

import kotlin.random.Random

enum class LineRef {
    MELODY, MELODY_ORN, COUNTER, BASS, PADS_FULL, PADS_ROOT, PADS_FIFTH,
    DRONE, PERC, SPARKLE, ACCOMP, RIFF, FLOURISH, TRUMPETER, DESTINY_FANFARE
}

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
    when (spec.family) {
        Family.BRAWL -> return planBrawlOrchestration(spec, hasTrumpeter)
        Family.THRONE -> return planThroneOrchestration(spec, hasTrumpeter)
        else -> {}
    }
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
    // Perc gains run hotter than sustained voices: a drum's energy is one transient, so at equal
    // gain it reads much quieter than a held string.
    val gPerc1 = 0.38f * (1f - solemnCount * 0.15f).coerceAtLeast(0.1f)
    val gDrone = (0.16f * (1f + wildCount.coerceAtMost(2) * 0.5f)).coerceAtMost(0.32f)
    val gThird = 0.22f
    val gPads = (0.14f * (1f + noblerCount.coerceAtMost(3) * 0.35f) *
        (1f + solemnCount.coerceAtMost(3) * 0.35f)).coerceAtMost(0.32f)
    val gPerc2 = 0.22f * (1f - solemnCount * 0.15f).coerceAtLeast(0.1f)
    val gWaits = 0.26f; val gSparkle = 0.16f
    val gBells = (0.22f * (1f + solemnCount.coerceAtMost(3) * 0.35f)).coerceAtMost(0.40f)
    val gTimp = 0.36f

    // L1 soloist: family-biased {harp, lute}; self-accompanies until the bass arrives at L3
    val soloist = weightedPick(rng, when (spec.family) {
        Family.GREENSLEEVES -> listOf(Voice.HARP to 0.7f, Voice.LUTE to 0.3f)
        Family.MINUET -> listOf(Voice.LUTE to 0.6f, Voice.HARP to 0.4f)
        Family.TINTAGEL -> listOf(Voice.HARP to 0.6f, Voice.LUTE to 0.4f)
        Family.ESTAMPIE -> listOf(Voice.LUTE to 0.7f, Voice.HARP to 0.3f)   // dance wants the pluck
        else -> listOf(Voice.HARP to 0.6f, Voice.LUTE to 0.4f)   // BRAWL/THRONE take bespoke plans (Tasks 3/5)
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
        Family.ESTAMPIE -> listOf(Voice.PANPIPES to 0.4f, Voice.RECORDER to 0.35f, Voice.VIELLE to 0.25f)
        else -> listOf(Voice.VIELLE to 0.6f, Voice.RECORDER to 0.4f)
    })
    used += second
    a += VoiceAssignment(second, LineRef.COUNTER, 2, 99, gSecond, 0.35f)

    // L3 bass takes the ground
    val bassPool = listOf(Voice.LUTE to 0.4f, Voice.VIOLA to 0.35f, Voice.SACKBUT to 0.25f).filter { it.first !in used }
    val bass = weightedPick(rng, if (bassPool.isEmpty()) listOf(Voice.VIOLA to 1f) else bassPool)
    used += bass
    a += VoiceAssignment(bass, LineRef.BASS, 3, 99, gBass, -0.25f)

    // L4 percussion I
    val percLevel = 4
    val perc1 = weightedPick(rng, listOf(Voice.BODHRAN to 0.6f, Voice.TABOR to 0.4f))
    a += VoiceAssignment(perc1, LineRef.PERC, percLevel, 99, gPerc1, -0.5f)
    if (spec.family == Family.TINTAGEL || spec.family == Family.ESTAMPIE) a += VoiceAssignment(Voice.NAKERS, LineRef.PERC, percLevel, 99, gPerc1 * 0.7f, 0.5f)

    // L5 drone bed
    val drone = weightedPick(rng, listOf(Voice.GURDY to 0.45f, Voice.ORGAN to 0.3f, Voice.VIOLA to 0.25f).filter { it.first != bass }
        .ifEmpty { listOf(Voice.GURDY to 1f) })
    val droneLevel = 5
    a += VoiceAssignment(drone, LineRef.DRONE, droneLevel, 99, gDrone, -0.7f)
    if (drone == Voice.GURDY) a += VoiceAssignment(Voice.GURDY, LineRef.SPARKLE, droneLevel, 99, gDrone * 1.6f, -0.7f)
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

/** BRAWL: a cumulative medieval speed-metal arrangement with sparse upper-level flourishes. */
private fun planBrawlOrchestration(spec: SongSpec, hasTrumpeter: Boolean): OrchestrationPlan {
    val a = mutableListOf<VoiceAssignment>()
    a += VoiceAssignment(Voice.NAKERS, LineRef.PERC, 1, 99, 0.46f, 0.4f)
    a += VoiceAssignment(Voice.TABOR, LineRef.PERC, 1, 99, 0.34f, -0.45f)
    a += VoiceAssignment(Voice.LUTE, LineRef.RIFF, 1, 99, 0.30f, -0.3f)
    a += VoiceAssignment(Voice.GURDY, LineRef.DRONE, 1, 99, 0.30f, -0.6f)
    a += VoiceAssignment(Voice.GURDY, LineRef.SPARKLE, 1, 99, 0.36f, -0.6f)
    a += VoiceAssignment(Voice.SHAWM, LineRef.MELODY, 1, 99, 0.40f, 0f)

    a += VoiceAssignment(Voice.VIOLA, LineRef.BASS, 2, 99, 0.32f, -0.2f)
    a += VoiceAssignment(Voice.BODHRAN, LineRef.PERC, 2, 99, 0.24f, -0.55f)
    a += VoiceAssignment(Voice.RECORDER, LineRef.FLOURISH, 2, 99, 0.12f, 0.55f)
    if (hasTrumpeter) a += VoiceAssignment(Voice.HORN, LineRef.TRUMPETER, 2, 99, 0.16f, 0.3f)

    a += VoiceAssignment(Voice.SACKBUT, LineRef.PADS_ROOT, 3, 99, 0.18f, -0.1f)
    a += VoiceAssignment(Voice.HORN, LineRef.PADS_FIFTH, 3, 99, 0.15f, 0.1f)
    a += VoiceAssignment(Voice.HORN, LineRef.FLOURISH, 3, 99, 0.18f, 0.25f)

    a += VoiceAssignment(Voice.TAMBOURINE, LineRef.PERC, 4, 99, 0.15f, 0.65f)
    a += VoiceAssignment(Voice.VIELLE, LineRef.COUNTER, 4, 99, 0.14f, 0.35f)
    a += VoiceAssignment(Voice.BELLS, LineRef.SPARKLE, 5, 99, 0.10f, 0.7f)
    a += VoiceAssignment(Voice.TIMPANI, LineRef.PERC, 5, 99, 0.28f, 0f)
    a += VoiceAssignment(Voice.PSALTERY, LineRef.RIFF, 6, 99, 0.10f, 0.45f, octave = 1)
    a += VoiceAssignment(Voice.FIDDLE2, LineRef.MELODY_ORN, 7, 99, 0.15f, 0.4f)
    a += VoiceAssignment(Voice.PANPIPES, LineRef.FLOURISH, 8, 99, 0.11f, 0.6f)
    a += VoiceAssignment(Voice.HARP, LineRef.FLOURISH, 9, 99, 0.11f, -0.5f)
    a += VoiceAssignment(Voice.ORGAN, LineRef.PADS_FULL, 10, 99, 0.08f, 0f)
    a += VoiceAssignment(Voice.CHOIR, LineRef.FLOURISH, 11, 99, 0.10f, 0.2f)
    return OrchestrationPlan(a, destinyFanfare = false)
}

/** THRONE: choir-led cinematic menace with tolling bells and a deliberately late melody. */
private fun planThroneOrchestration(spec: SongSpec, hasTrumpeter: Boolean): OrchestrationPlan {
    val a = mutableListOf<VoiceAssignment>()
    a += VoiceAssignment(Voice.CHOIR, LineRef.PADS_ROOT, 1, 99, 0.30f, -0.2f)
    a += VoiceAssignment(Voice.CHOIR, LineRef.PADS_FIFTH, 1, 99, 0.24f, 0.2f)
    a += VoiceAssignment(Voice.GURDY, LineRef.DRONE, 1, 99, 0.26f, -0.7f)
    a += VoiceAssignment(Voice.VIOLA, LineRef.BASS, 1, 99, 0.34f, -0.3f)
    a += VoiceAssignment(Voice.TIMPANI, LineRef.PERC, 1, 99, 0.44f, 0f)
    a += VoiceAssignment(Voice.BELLS, LineRef.SPARKLE, 1, 99, 0.26f, 0.6f)
    a += VoiceAssignment(Voice.VIELLE, LineRef.MELODY, 4, 99, 0.34f, 0.1f, octave = 1)
    a += VoiceAssignment(Voice.CHOIR, LineRef.COUNTER, 7, 99, 0.18f, 0.35f)
    a += VoiceAssignment(Voice.ORGAN, LineRef.PADS_FULL, 10, 99, 0.12f, 0f)
    if (hasTrumpeter) a += VoiceAssignment(Voice.HORN, LineRef.TRUMPETER, 2, 99, 0.20f, 0.3f)
    return OrchestrationPlan(a, destinyFanfare = false)
}

// ---- Pattern generators (all pure functions of the spec - level-free, rng-free) ----

fun percussionEvents(spec: SongSpec, voice: Voice, wilder: Boolean): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    for (bar in 0 until spec.totalBars) {
        val base = bar * bpb
        when (voice) {
            Voice.BODHRAN -> {
                if (spec.family == Family.BRAWL) {
                    for (beat in 0 until spec.beatsPerBar) {
                        out += NoteEvent(base + beat, 0.3f, 57, if (beat % 2 == 0) 0.9f else 0.65f)
                    }
                } else {
                    out += NoteEvent(base, 0.4f, 57, 1f)
                    when (spec.beatsPerBar) {
                        6 -> { out += NoteEvent(base + 3f, 0.4f, 57, 0.7f); if (wilder) { out += NoteEvent(base + 4.5f, 0.3f, 57, 0.5f); out += NoteEvent(base + 5.5f, 0.3f, 57, 0.5f) } }
                        4 -> { out += NoteEvent(base + 2f, 0.4f, 57, 0.7f); if (wilder) { out += NoteEvent(base + 2.5f, 0.3f, 57, 0.5f); out += NoteEvent(base + 3.5f, 0.3f, 57, 0.5f) } }
                        else -> if (wilder) { out += NoteEvent(base + 1.5f, 0.3f, 57, 0.5f); out += NoteEvent(base + 2.5f, 0.3f, 57, 0.5f) }
                    }
                }
            }
            Voice.TABOR -> {
                if (spec.family == Family.BRAWL) {
                    out += NoteEvent(base + 1f, 0.3f, 57, 0.9f)
                    out += NoteEvent(base + 3f, 0.3f, 57, 1f)
                    if (bar % 4 == 3) out += NoteEvent(base + 3.5f, 0.2f, 57, 0.65f)
                } else {
                    out += NoteEvent(base, 0.4f, 57, 1f)
                    when (spec.beatsPerBar) {
                        6 -> { out += NoteEvent(base + 3f, 0.4f, 57, 0.7f); if (wilder) { out += NoteEvent(base + 4.5f, 0.3f, 57, 0.5f); out += NoteEvent(base + 5.5f, 0.3f, 57, 0.5f) } }
                        4 -> { out += NoteEvent(base + 2f, 0.4f, 57, 0.7f); if (wilder) { out += NoteEvent(base + 2.5f, 0.3f, 57, 0.5f); out += NoteEvent(base + 3.5f, 0.3f, 57, 0.5f) } }
                        else -> if (wilder) { out += NoteEvent(base + 1.5f, 0.3f, 57, 0.5f); out += NoteEvent(base + 2.5f, 0.3f, 57, 0.5f) }
                    }
                }
            }
            Voice.TAMBOURINE -> {
                if (spec.family == Family.BRAWL) {
                    var beat = 0f
                    while (beat < bpb - 1e-3f) {
                        out += NoteEvent(base + beat, 0.2f, 57, if (beat % 2f == 0f) 0.75f else 0.5f)
                        beat += 0.5f
                    }
                } else {
                    val offs = when (spec.beatsPerBar) { 6 -> listOf(1.5f, 4.5f); 4 -> listOf(1f, 3f); else -> listOf(1f, 2f) }
                    for (o in offs) out += NoteEvent(base + o, 0.25f, 57, 0.8f)
                }
            }
            Voice.NAKERS -> if (spec.family == Family.BRAWL) {
                // Double-time gallop: the wilder double-hit is the FLOOR here, not the ceiling.
                var b = 0f
                while (b < bpb - 1e-3f) {
                    val accent = b % 2f == 0f
                    out += NoteEvent(base + b, 0.25f, if (accent) 56 else 57, if (accent) 1f else 0.6f)
                    b += 0.5f
                }
            } else if (bar % 4 == 0) {
                out += NoteEvent(base, 0.4f, 56, 0.9f); out += NoteEvent(base + 0.5f, 0.3f, 57, 0.6f)
            }
            Voice.TIMPANI -> if (spec.family == Family.THRONE) {
                val groundRoot = degreeToMidi(spec, spec.ground[bar % 8].bassDegree)
                if (bar % 2 == 0) out += NoteEvent(base, 2.0f, groundRoot, 1f)
                if (bar % 8 == 7) {
                    out += NoteEvent(base + bpb - 1f, 1.0f, spec.finalMidi - 5, 0.8f)
                }
            } else if (spec.family == Family.BRAWL) {
                if (bar % 4 == 0) out += NoteEvent(base, 1.0f, degreeToMidi(spec, spec.ground[bar % 8].bassDegree), 0.9f)
                if (bar % 4 == 3) {
                    for (r in 0 until 4) {
                        out += NoteEvent(base + bpb - 1f + r * 0.25f, 0.18f, spec.finalMidi, 0.55f + 0.1f * r)
                    }
                }
            } else {
                if (bar % 8 == 0) out += NoteEvent(base, 1.2f, spec.finalMidi, 1f)
                if (bar % 8 == 7) {
                    for (r in 0 until 6) {
                        out += NoteEvent(
                            base + bpb - 1f + r * (1f / 6f),
                            0.15f,
                            spec.finalMidi,
                            0.4f + 0.08f * r
                        )
                    }
                }
            }
            else -> {}
        }
    }
    return out
}

fun droneEvents(spec: SongSpec): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    if (spec.family == Family.BRAWL || spec.family == Family.THRONE) {
        for (bar in 0 until spec.totalBars) {
            val chord = groundChordMidis(spec, spec.ground[bar % 8].bassDegree)
            out += NoteEvent(bar * bpb, bpb, chord[0], 0.9f)
            out += NoteEvent(bar * bpb, bpb, chord[1], 0.7f)
        }
        return out
    }
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
    for (bar in 0 until spec.totalBars) {
        val midi = if (spec.family == Family.BRAWL || spec.family == Family.THRONE) {
            groundChordMidis(spec, spec.ground[bar % 8].bassDegree)[0]
        } else {
            spec.finalMidi
        }
        for (s in strong) out += NoteEvent(bar * bpb + s, 0.09f, midi, 0.9f)
    }
    return out
}

fun sparkleEvents(spec: SongSpec): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    val step = if (spec.beatsPerBar == 6) 1f else 0.5f
    for (bar in 0 until spec.totalBars) {
        val chord = groundChordMidis(spec, spec.ground[bar % 8].bassDegree).map { it + 24 }
        val arp = intArrayOf(chord[0], chord[1], chord[2], chord[1])
        var beat = 0f; var i = 0
        while (beat < bpb - 1e-3f) {
            out += NoteEvent(bar * bpb + beat, step * 1.5f, arp[i % 4], 0.7f)
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
        val chord = groundChordMidis(spec, g)
        out += NoteEvent(bar * bpb, bpb, chord[0], 0.9f)                    // bass note, downbeat
        val mid = if (spec.beatsPerBar == 6) 3f else spec.beatsPerBar / 2f
        out += NoteEvent(bar * bpb + mid * 0.5f, 1f, chord[1], 0.55f)       // broken chord: fifth
        out += NoteEvent(bar * bpb + mid, 1f, chord[2], 0.5f)               // octave
    }
    return out
}

fun brawlRiffEvents(spec: SongSpec): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    for (bar in 0 until spec.totalBars) {
        val chord = groundChordMidis(spec, spec.ground[bar % 8].bassDegree)
        val root = chord[0] + 12
        var fifth = chord[1]
        while (fifth < root) fifth += 12
        val pattern = intArrayOf(root, root, fifth, root, root, root + 12, fifth, root)
        for (i in pattern.indices) {
            out += NoteEvent(bar * bpb + i * 0.5f, 0.34f, pattern[i], if (i % 4 == 0) 0.95f else 0.7f)
        }
    }
    return out
}

fun brawlChordStabEvents(spec: SongSpec, fifth: Boolean): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    for (bar in 0 until spec.totalBars) {
        val chord = groundChordMidis(spec, spec.ground[bar % 8].bassDegree)
        val midi = chord[if (fifth) 1 else 0]
        out += NoteEvent(bar * bpb, 0.35f, midi, 0.9f)
        out += NoteEvent(bar * bpb + 2f, 0.3f, midi, 0.7f)
        if (bar % 4 == 3) out += NoteEvent(bar * bpb + 3.5f, 0.22f, midi, 0.75f)
    }
    return out
}

fun brawlFlourishEvents(spec: SongSpec, voice: Voice): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    if (voice == Voice.CHOIR) {
        for (bar in 0 until spec.totalBars step 8) {
            val chord = groundChordMidis(spec, spec.ground[bar % 8].bassDegree)
            out += NoteEvent(bar * bpb, 2f, chord[0] + 12, 0.75f)
            out += NoteEvent(bar * bpb + 2f, 2f, chord[1] + 12, 0.65f)
        }
        return out
    }
    if (voice == Voice.HORN) {
        for (bar in 0 until spec.totalBars step 4) {
            val chord = groundChordMidis(spec, spec.ground[bar % 8].bassDegree)
            out += NoteEvent(bar * bpb, 0.5f, chord[0] + 12, 0.85f)
            out += NoteEvent(bar * bpb + 0.5f, 0.5f, chord[1] + 12, 0.9f)
            out += NoteEvent(bar * bpb + 1f, 1f, chord[2] + 12, 1f)
        }
        return out
    }
    for (bar in 3 until spec.totalBars step 4) {
        val g = spec.ground[bar % 8].bassDegree
        val notes = intArrayOf(
            degreeToMidi(spec, g + 7),
            degreeToMidi(spec, g + 9),
            degreeToMidi(spec, g + 11),
            degreeToMidi(spec, g + 14)
        )
        val start = bar * bpb + bpb - 1f
        for (i in notes.indices) out += NoteEvent(start + i * 0.25f, 0.22f, notes[i], 0.7f + 0.08f * i)
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
        // Third comes from the mode — a hardcoded major third clashed in dorian/aeolian pieces.
        val triad = intArrayOf(0, spec.mode.steps[2], 7, 12)
        for ((i, t) in triad.withIndex()) out += NoteEvent(base + i * 0.25f, 0.8f, spec.finalMidi + 12 + t, 0.95f)
        bar += 8
    }
    return out
}
