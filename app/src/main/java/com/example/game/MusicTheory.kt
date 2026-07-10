package com.example.game

import kotlin.random.Random

enum class Family { GREENSLEEVES, MINUET, TINTAGEL }

enum class Mode(val steps: IntArray) {
    DORIAN(intArrayOf(0, 2, 3, 5, 7, 9, 10)),
    AEOLIAN(intArrayOf(0, 2, 3, 5, 7, 8, 10)),
    IONIAN(intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    MIXOLYDIAN(intArrayOf(0, 2, 4, 5, 7, 9, 10))
}

data class GroundBar(val bassDegree: Int)

data class SongSpec(
    val seed: Long, val family: Family, val mode: Mode, val finalMidi: Int,
    val beatsPerBar: Int, val secondsPerBeat: Float, val bpm: Int,
    val ground: List<GroundBar>, val totalBars: Int,
    val ornamentDensity: Float, val moods: List<String>
)

fun modeOffset(mode: Mode, degree: Int): Int {
    val oct = Math.floorDiv(degree, 7)
    val idx = Math.floorMod(degree, 7)
    return 12 * oct + mode.steps[idx]
}

fun degreeToMidi(spec: SongSpec, degree: Int): Int = spec.finalMidi + modeOffset(spec.mode, degree)

fun melodyRng(seed: Long) = Random(seed)
fun orchRng(seed: Long) = Random(seed xor 0x5DEECE66DL)
fun humaniseRng(seed: Long) = Random(seed xor 0x2545F4914F6CDD1DL)

private val GREENSLEEVES_GROUNDS = listOf(
    listOf(0, 6, 0, 4, 0, 6, 4, 0),   // romanesca
    listOf(0, 6, 0, 4, 5, 6, 4, 0)    // romanesca with VI colour
)
private val MINUET_GROUNDS = listOf(
    listOf(0, 4, 0, 4, 0, 3, 4, 0),   // I V I V / I IV V I
    listOf(0, 4, 5, 4, 0, 3, 4, 0)    // vi-colour variant
)
private val TINTAGEL_GROUNDS = listOf(
    listOf(0, 0, 5, 6, 0, 5, 6, 0),   // broad i i VI VII
    listOf(0, 5, 0, 6, 0, 5, 6, 0)
)

fun resolveSongSpec(seed: Long, moods: List<String>): SongSpec {
    val rng = Random(seed * 31L + 7L)   // spec stream, separate from melody/orch
    val family = Family.values()[rng.nextInt(3)]

    var mode: Mode
    var bpm: Int
    val beatsPerBar: Int
    val totalBars: Int
    val groundDegrees: List<Int>
    when (family) {
        Family.GREENSLEEVES -> {
            mode = Mode.DORIAN
            bpm = 56 + rng.nextInt(17)           // 56-72 (dotted crotchet)
            beatsPerBar = 6; totalBars = 32
            groundDegrees = GREENSLEEVES_GROUNDS[rng.nextInt(GREENSLEEVES_GROUNDS.size)]
        }
        Family.MINUET -> {
            mode = if (rng.nextBoolean()) Mode.IONIAN else Mode.MIXOLYDIAN
            bpm = 92 + rng.nextInt(21)           // 92-112
            beatsPerBar = 3; totalBars = 32
            groundDegrees = MINUET_GROUNDS[rng.nextInt(MINUET_GROUNDS.size)]
        }
        Family.TINTAGEL -> {
            mode = if (rng.nextBoolean()) Mode.AEOLIAN else Mode.DORIAN
            bpm = 60 + rng.nextInt(17)           // 60-76
            beatsPerBar = 4; totalBars = 16
            groundDegrees = TINTAGEL_GROUNDS[rng.nextInt(TINTAGEL_GROUNDS.size)]
        }
    }
    val finalMidi = 45 + rng.nextInt(8)          // A2-G#3
    var ornament = when (family) {
        Family.MINUET -> 0.6f; Family.GREENSLEEVES -> 0.4f; Family.TINTAGEL -> 0.25f
    }

    // Mood deltas (later-wins on conflicts, applied in list order)
    for (m in moods) when (m) {
        "More Tempo"  -> bpm += 12
        "Merrier"     -> { mode = brighten(mode); ornament = (ornament + 0.25f).coerceAtMost(1f) }
        "More Solemn" -> { mode = Mode.AEOLIAN; bpm -= 8; ornament = (ornament - 0.1f).coerceAtLeast(0.1f) }
        "Nobler"      -> bpm -= 4
        // "Wilder" affects orchestration + division density (Orchestrator/MelodyGenerator)
    }

    val spb = if (beatsPerBar == 6) 60f / bpm / 3f else 60f / bpm
    return SongSpec(
        seed = seed, family = family, mode = mode, finalMidi = finalMidi,
        beatsPerBar = beatsPerBar, secondsPerBeat = spb, bpm = bpm,
        ground = groundDegrees.map { GroundBar(it) }, totalBars = totalBars,
        ornamentDensity = ornament, moods = moods
    )
}

private fun brighten(m: Mode): Mode = when (m) {
    Mode.AEOLIAN -> Mode.DORIAN
    Mode.DORIAN -> Mode.MIXOLYDIAN
    else -> m
}
