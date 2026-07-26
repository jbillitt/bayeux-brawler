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

/** Root, modal fifth, and octave for the ground chord. */
fun groundChordMidis(spec: SongSpec, groundDegree: Int): List<Int> {
    val root = degreeToMidi(spec, groundDegree)
    return listOf(root, degreeToMidi(spec, groundDegree + 4), root + 12)
}

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
private val BRAWL_AEOLIAN_GROUNDS = listOf(
    listOf(0, 6, 0, 4, 0, 6, 4, 0),   // i bVII i v / i bVII v i
    listOf(0, 6, 0, 6, 0, 4, 6, 0),   // double-tonic hammer with a v turn
    listOf(0, 4, 0, 4, 0, 6, 4, 0)    // i v alternation, bVII-v-i turnaround
)
private val BRAWL_PHRYGIAN_GROUNDS = listOf(
    listOf(0, 6, 0, 1, 0, 6, 1, 0),   // i bVII i bII — flat-two metal sting
    listOf(0, 6, 0, 6, 0, 1, 6, 0),
    listOf(0, 1, 0, 1, 0, 6, 1, 0)
)
// The heroic half of the brawl. Dorian is the fighting mode — minor, but with the bright
// natural 6 that keeps a riff from sulking (the whole NWOBHM sound). Mixolydian is the
// flat-7 major of a power-metal chorus: this is where the organ gets to be triumphant
// instead of ominous. Both climb to bVII/IV and cadence home rather than circling the tonic.
private val BRAWL_DORIAN_GROUNDS = listOf(
    listOf(0, 6, 3, 0, 0, 6, 3, 0),   // i bVII IV i — the gallop that goes somewhere
    listOf(0, 3, 6, 0, 0, 3, 4, 0),   // i IV bVII i / i IV v i
    listOf(0, 0, 6, 3, 0, 6, 3, 0)    // double-tonic hammer, then the lift
)
private val BRAWL_MIXO_GROUNDS = listOf(
    listOf(0, 6, 3, 0, 0, 6, 4, 0),   // I bVII IV I — the entrance-music cadence
    listOf(0, 3, 0, 6, 0, 3, 6, 0),   // I IV I bVII, hands in the air
    listOf(0, 6, 0, 3, 4, 3, 6, 0)    // a full turn that lands on the tonic
)
private val THRONE_GROUNDS = listOf(
    // Rising, cadential progressions — a coronation, not a cortege. Both of the old grounds
    // opened on a descending tetrachord (0-6-5-4), which is the lamento bass: the stock
    // Baroque ground for grief. No tempo or mode change survives that in the bass.
    listOf(0, 4, 5, 3, 0, 3, 4, 0),   // I-V-vi-IV / I-IV-V-I — the processional
    listOf(0, 3, 0, 4, 5, 3, 4, 0),   // I-IV-I-V / vi-IV-V-I — answered by a full cadence
    listOf(0, 5, 3, 4, 0, 4, 3, 0),   // I-vi-IV-V, the stately turn
    listOf(0, 6, 5, 4, 0, 5, 4, 0)    // one tetrachord kept, for the sombre coronation
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
            // The hero is winning. Phrygian is the mode of menace — its flat 2 is what every
            // horror cue is built on — and at 40% of rolls it was setting the tone for the
            // whole mode, which came out oppressive rather than exciting. It survives as a
            // rare villain colour; the bulk is now Dorian (heroic minor) and Mixolydian
            // (triumphant flat-7 major), the two modes wrestling entrances actually live in.
            mode = when (rng.nextInt(10)) {
                0 -> Mode.PHRYGIAN               // 10% — the heel's theme
                in 1..2 -> Mode.AEOLIAN          // 20% — straight minor heavy
                in 3..6 -> Mode.DORIAN           // 40% — the fighting mode
                else -> Mode.MIXOLYDIAN          // 30% — organ gets to be triumphant
            }
            // 180-200 was a thrash gallop. Wrestling-entrance metal is slower and heavier — the
            // riff wants room to land, and at 180 the 16th-note double kick was a blur rather
            // than two distinct feet. Nudged up from 118-136: a hero smashing things has a
            // bounce to it. (The clamp below must move with this or it does nothing.)
            bpm = 128 + rng.nextInt(19)          // 128-146
            beatsPerBar = 4; totalBars = 16
            val grounds = when (mode) {
                Mode.PHRYGIAN -> BRAWL_PHRYGIAN_GROUNDS
                Mode.DORIAN -> BRAWL_DORIAN_GROUNDS
                Mode.MIXOLYDIAN -> BRAWL_MIXO_GROUNDS
                else -> BRAWL_AEOLIAN_GROUNDS
            }
            groundDegrees = grounds[rng.nextInt(grounds.size)]
        }
        Family.THRONE -> {                       // royal processional — throne mode only
            // Regal, not ominous: major (Ionian) mostly, fanfare-flat-7 (Mixolydian) sometimes.
            // Weighted to Ionian — an even split let half the coronations come out modal and grim.
            mode = if (rng.nextInt(10) < 7) Mode.IONIAN else Mode.MIXOLYDIAN
            // 66-76 was a funeral, not a coronation. A processional march walks at ~90: fast
            // enough to have a stride, slow enough to be stately.
            bpm = 86 + rng.nextInt(13)           // 86-98
            beatsPerBar = 4; totalBars = 16
            groundDegrees = THRONE_GROUNDS[rng.nextInt(THRONE_GROUNDS.size)]
        }
    }
    var finalMidi = 45 + rng.nextInt(8)          // A2-G#3
    if (family == Family.THRONE) finalMidi -= 1  // stately, but no longer buried in the sub-bass murk
    var ornament = when (family) {
        Family.MINUET -> 0.6f; Family.GREENSLEEVES -> 0.4f; Family.TINTAGEL -> 0.25f
        Family.ESTAMPIE -> 0.5f
        Family.BRAWL -> 0.3f                     // already fast; divisions would smear
        Family.THRONE -> 0.35f                   // courtly graces on the phrase-ends
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
        family == Family.BRAWL -> 146   // moves with the 128-146 roll above, or it caps it
        family == Family.THRONE -> 98
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
