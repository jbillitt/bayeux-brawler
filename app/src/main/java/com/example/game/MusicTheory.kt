package com.example.game

import kotlin.random.Random

enum class Family { GREENSLEEVES, MINUET, TINTAGEL, ESTAMPIE, BRAWL, THRONE }

enum class Mode(val steps: IntArray) {
    DORIAN(intArrayOf(0, 2, 3, 5, 7, 9, 10)),
    AEOLIAN(intArrayOf(0, 2, 3, 5, 7, 8, 10)),
    IONIAN(intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    MIXOLYDIAN(intArrayOf(0, 2, 4, 5, 7, 9, 10)),
    PHRYGIAN(intArrayOf(0, 1, 3, 5, 7, 8, 10))   // the flat-2 sting for BRAWL cadences
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
    listOf(0, 6, 0, 4, 5, 6, 4, 0),   // romanesca with VI colour
    listOf(0, 6, 0, 4, 2, 6, 4, 0)    // passamezzo antico (i VII i V III VII V i)
)
private val MINUET_GROUNDS = listOf(
    listOf(0, 4, 0, 4, 0, 3, 4, 0),   // I V I V / I IV V I
    listOf(0, 4, 5, 4, 0, 3, 4, 0),   // vi-colour variant
    listOf(0, 3, 0, 4, 5, 3, 4, 0)    // plagal-lean variant with vi colour
)
private val TINTAGEL_GROUNDS = listOf(
    listOf(0, 0, 5, 6, 0, 5, 6, 0),   // broad i i VI VII
    listOf(0, 5, 0, 6, 0, 5, 6, 0),
    listOf(0, 4, 0, 6, 2, 6, 4, 0)    // folia-shaped (i V i VII III VII V i)
)
private val ESTAMPIE_GROUNDS = listOf(
    listOf(0, 6, 0, 6, 0, 5, 6, 0),   // double-tonic i VII, VI colour at the turn
    listOf(0, 6, 5, 6, 0, 6, 5, 0)
)
private val BRAWL_GROUNDS = listOf(
    listOf(0, 6, 0, 4, 0, 6, 0, 4),   // i bVII i V riff, twice round
    listOf(0, 6, 0, 6, 0, 6, 4, 0),   // double-tonic hammer with a V turn
    listOf(0, 4, 0, 4, 0, 6, 4, 0)    // i V i V power alternation
)
private val THRONE_GROUNDS = listOf(
    listOf(0, 6, 5, 4, 0, 6, 5, 4),   // descending tetrachord i bVII bVI V (lament bass)
    listOf(0, 6, 5, 4, 0, 5, 3, 4)    // tetrachord answered by bVI iv V
)

// Normal rotation only — BRAWL and THRONE are trigger-selected, never rolled.
private val ROTATION = listOf(Family.GREENSLEEVES, Family.MINUET, Family.TINTAGEL, Family.ESTAMPIE)

fun resolveSongSpec(seed: Long, moods: List<String>, brawl: Boolean = false, throne: Boolean = false): SongSpec {
    val rng = Random(seed * 31L + 7L)   // spec stream, separate from melody/orch
    // Throne wins over fists: the lord isn't punching.
    val family = when {
        throne -> Family.THRONE
        brawl -> Family.BRAWL
        else -> ROTATION[rng.nextInt(ROTATION.size)]
    }

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
        Family.ESTAMPIE -> {                     // fast duple dance — the consort's up-tempo leg
            mode = if (rng.nextBoolean()) Mode.DORIAN else Mode.MIXOLYDIAN
            bpm = 112 + rng.nextInt(25)          // 112-136
            beatsPerBar = 4; totalBars = 32
            groundDegrees = ESTAMPIE_GROUNDS[rng.nextInt(ESTAMPIE_GROUNDS.size)]
        }
        Family.BRAWL -> {                        // medieval speed metal — fists only
            mode = if (rng.nextInt(10) < 3) Mode.PHRYGIAN else Mode.AEOLIAN
            bpm = 168 + rng.nextInt(17)          // 168-184
            beatsPerBar = 4; totalBars = 16
            groundDegrees = BRAWL_GROUNDS[rng.nextInt(BRAWL_GROUNDS.size)]
        }
        Family.THRONE -> {                       // epic cinematic thriller — throne mode only
            mode = if (rng.nextBoolean()) Mode.DORIAN else Mode.AEOLIAN
            bpm = 66 + rng.nextInt(11)           // 66-76
            beatsPerBar = 4; totalBars = 16
            groundDegrees = THRONE_GROUNDS[rng.nextInt(THRONE_GROUNDS.size)]
        }
    }
    var finalMidi = 45 + rng.nextInt(8)          // A2-G#3
    if (family == Family.THRONE) finalMidi -= 4  // the court sits deep
    var ornament = when (family) {
        Family.MINUET -> 0.6f; Family.GREENSLEEVES -> 0.4f; Family.TINTAGEL -> 0.25f
        Family.ESTAMPIE -> 0.5f
        Family.BRAWL -> 0.3f                     // already fast; divisions would smear
        Family.THRONE -> 0.15f                   // sparse, ominous
    }

    // Mood deltas (applied in list order, stackable). Themed families ignore moods:
    // BRAWL and THRONE are fixed-character replacements, not flavours of the run tune.
    if (family != Family.BRAWL && family != Family.THRONE) {
        for (m in moods) when (m) {
            "More Tempo"  -> bpm = (bpm * 1.25).toInt()
            "Merrier"     -> { mode = brighten(mode); ornament = (ornament + 0.35f).coerceAtMost(1.5f) }
            "More Solemn" -> { mode = darken(mode); bpm = (bpm * 0.85).toInt(); ornament = (ornament - 0.2f).coerceAtLeast(0.05f) }
            "Nobler"      -> { bpm = (bpm * 0.90).toInt(); finalMidi -= 3 }
            "Wilder"      -> { bpm = (bpm * 1.10).toInt(); ornament = (ornament + 0.4f).coerceAtMost(2.0f) }
        }
    }
    // Stacked tempo moods multiply without bound; past these caps the note tails smear across
    // chord changes and everything reads as discord. Nobler stacks -3 each pick; below MIDI 39
    // the counter-voice falls out of its playable register.
    bpm = bpm.coerceIn(40, when {
        family == Family.BRAWL -> 184
        family == Family.THRONE -> 76
        beatsPerBar == 6 -> 84
        else -> 150
    })
    finalMidi = finalMidi.coerceIn(39, 52)

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
    else -> Mode.IONIAN
}

private fun darken(m: Mode): Mode = when (m) {
    Mode.IONIAN -> Mode.MIXOLYDIAN
    Mode.MIXOLYDIAN -> Mode.DORIAN
    else -> Mode.AEOLIAN
}
