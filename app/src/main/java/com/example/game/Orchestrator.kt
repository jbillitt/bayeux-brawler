package com.example.game

import kotlin.random.Random

enum class LineRef {
    MELODY, MELODY_ORN, COUNTER, BASS, PADS_FULL, PADS_ROOT, PADS_FIFTH,
    DRONE, PERC, SPARKLE, ACCOMP, RIFF, FLOURISH, TRUMPETER, DESTINY_FANFARE, STRUM,

    /**
     * Whoever is keeping the subdivision this song — a job, not an instrument.
     *
     * The shaker and the tambourine both used to be assigned unconditionally, so every run had
     * both and the shaker became the sound of the game rather than one colour in it. This line is
     * handed to exactly one voice per song, and four of them can hold it: shaker, tambourine, a
     * hand drum playing the same interlocking figure, or tuned kettledrums doing it as DUM-dum.
     */
    PERC_LIGHT
}

data class VoiceAssignment(
    val voice: Voice, val line: LineRef, val enterLevel: Int, val exitLevel: Int = 99,
    val gain: Float, val pan: Float, val octave: Int = 0, val transposeDegrees: Int = 0,
    /**
     * Which strains this voice plays: -1 every strain, 0 the even ones, 1 the odd ones.
     *
     * Two voices given the same line and opposite parities hand the tune back and forth — the
     * recorder takes the verse, the vielle answers it. Levels alone could not express this: they
     * say WHEN in the run a voice joins, not WHERE in the song it plays.
     */
    val strainParity: Int = -1
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

/**
 * Gain allowance for voices that are quiet by construction rather than by choice.
 *
 * A panpipe is a stopped tube with almost no fundamental — it reads as breath. Given the same
 * gain as a shawm it is simply not there, which is exactly what happened: panpipes sat in three
 * separate instrument pools and were never once heard in play.
 */
private fun pipeBoost(v: Voice): Float = if (v == Voice.PANPIPES) 1.45f else 1f

fun planOrchestration(spec: SongSpec, hasTrumpeter: Boolean, rng: Random): OrchestrationPlan {
    when (spec.family) {
        Family.BRAWL -> return planBrawlOrchestration(spec, hasTrumpeter, rng)
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
    // A sung piece is sung. Organum, conductus and chant were voices first and instruments second
    // (often not at all), so the choir takes the tune from the very first level rather than
    // arriving at 14 as a late colour — which is where the only other route to CHOIR put it, and
    // is why a plainchant setting could come out as a lute solo.
    if (spec.piece?.vocal == true) {
        used += Voice.CHOIR
        a += VoiceAssignment(Voice.CHOIR, LineRef.MELODY, 1, 3, gMel * 1.05f, 0f)
        a += VoiceAssignment(Voice.CHOIR, LineRef.MELODY_ORN, 3, 99, gMel * 1.05f, 0f)
        // The held tenor underneath — one voice on the ground, which is what an organum IS.
        a += VoiceAssignment(Voice.CHOIR, LineRef.DRONE, 2, 99, gMel * 0.55f, -0.3f)
    }
    a += VoiceAssignment(soloist, LineRef.MELODY, 1, 3, gMel, 0f)
    // MELODY_ORN from L3 is assigned below, once we know whether the tune changes hands.
    a += VoiceAssignment(soloist, LineRef.ACCOMP, 1, 3, gAcc, 0f)

    // L2 second voice
    val second = weightedPick(rng, when (spec.family) {
        Family.TINTAGEL -> listOf(Voice.VIELLE to 0.45f, Voice.OBOE to 0.25f, Voice.RECORDER to 0.18f, Voice.PANPIPES to 0.12f)
        Family.MINUET -> listOf(Voice.RECORDER to 0.38f, Voice.OBOE to 0.27f, Voice.VIELLE to 0.2f, Voice.PANPIPES to 0.15f)
        Family.GREENSLEEVES -> listOf(Voice.RECORDER to 0.3f, Voice.VIELLE to 0.3f, Voice.OBOE to 0.25f, Voice.PANPIPES to 0.15f)
        Family.ESTAMPIE -> listOf(Voice.PANPIPES to 0.34f, Voice.RECORDER to 0.28f, Voice.VIELLE to 0.2f, Voice.OBOE to 0.18f)
        else -> listOf(Voice.VIELLE to 0.45f, Voice.RECORDER to 0.3f, Voice.OBOE to 0.25f)
    })
    used += second
    a += VoiceAssignment(second, LineRef.COUNTER, 2, 99, gSecond, 0.35f)

    // The tune changes hands. Most songs give the melody to one instrument for their whole length,
    // which is what makes a four-minute loop wear out: the ear stops being told anything new. Here
    // a wind or a fiddle takes the odd strains off the soloist and hands them back, so the same
    // tune arrives in a different colour halfway through. Not every song — a piece that never
    // changes hands is a legitimate piece, and the contrast needs somewhere to be measured from.
    if (rng.nextFloat() < 0.55f) {
        val relief = weightedPick(rng, listOf(
            Voice.RECORDER to 0.28f, Voice.VIELLE to 0.24f, Voice.OBOE to 0.2f,
            Voice.PANPIPES to 0.16f, Voice.PSALTERY to 0.12f
        ).filter { it.first != second && it.first != soloist }.ifEmpty { listOf(Voice.RECORDER to 1f) })
        used += relief
        // The soloist keeps the even strains, the newcomer answers on the odd ones. Both enter at
        // L3 together — a hand-off where one side is missing is just a tune that stops.
        a += VoiceAssignment(soloist, LineRef.MELODY_ORN, 3, 99, gMel, 0f, strainParity = 0)
        a += VoiceAssignment(relief, LineRef.MELODY_ORN, 3, 99, gMel * 0.92f * pipeBoost(relief), 0.2f, strainParity = 1)
    } else {
        a += VoiceAssignment(soloist, LineRef.MELODY_ORN, 3, 99, gMel, 0f)
    }

    // L3 bass takes the ground
    val bassPool = listOf(
        Voice.CELLO to 0.4f, Voice.LUTE to 0.28f, Voice.VIOLA to 0.2f, Voice.SACKBUT to 0.12f
    ).filter { it.first !in used }
    val bass = weightedPick(rng, if (bassPool.isEmpty()) listOf(Voice.VIOLA to 1f) else bassPool)
    used += bass
    a += VoiceAssignment(bass, LineRef.BASS, 3, 99, gBass, -0.25f)

    // Not every song's harper strums. Roughly two in three do, and the ones that do have their
    // own habit about where in the phrase it falls (see strumPlacementFor) — so two runs of the
    // same family are told apart by it rather than sounding like one another.
    if (rng.nextFloat() < 0.65f) {
        a += VoiceAssignment(soloist, LineRef.STRUM, 3 + rng.nextInt(3), 99, gMel * 0.62f, 0.1f)
    }

    // L4 percussion I
    val percLevel = 4
    val perc1 = weightedPick(rng, listOf(Voice.BODHRAN to 0.6f, Voice.TABOR to 0.4f))
    a += VoiceAssignment(perc1, LineRef.PERC, percLevel, 99, gPerc1, -0.5f)
    // Nakers belong to the dance families, but a quarter of the others get them too — hard-gating
    // them meant a Greensleeves or a Minuet could never field a pair of kettle drums at all.
    val nakersHere = spec.family == Family.TINTAGEL || spec.family == Family.ESTAMPIE ||
        rng.nextFloat() < 0.25f
    if (nakersHere) a += VoiceAssignment(Voice.NAKERS, LineRef.PERC, percLevel, 99, gPerc1 * 0.7f, 0.5f)

    // L5 drone bed
    val drone = weightedPick(rng, listOf(Voice.GURDY to 0.45f, Voice.ORGAN to 0.3f, Voice.VIOLA to 0.25f).filter { it.first != bass }
        .ifEmpty { listOf(Voice.GURDY to 1f) })
    val droneLevel = 5
    a += VoiceAssignment(drone, LineRef.DRONE, droneLevel, 99, gDrone, -0.7f)
    // The buzzing-bridge accent, at 0.6 of the drone rather than 1.6x it. At the old level the
    // coup de glotte was the loudest event in the bar and pulled the ear off the tune every time
    // it fired — a hurdy-gurdy's buzz is a rhythmic seasoning, not a lead instrument.
    if (drone == Voice.GURDY) a += VoiceAssignment(Voice.GURDY, LineRef.SPARKLE, droneLevel, 99, gDrone * 0.6f, -0.7f)
    // NOTE: gurdy buzz accents ride the SPARKLE slot pan; the facade maps GURDY+SPARKLE to gurdyBuzzEvents.

    // L6 third voice: divisions on the repeats
    val thirdPool = listOf(Voice.RECORDER, Voice.VIELLE, Voice.PANPIPES, Voice.PSALTERY, Voice.OBOE).filter { it !in used }
    val third = thirdPool[rng.nextInt(thirdPool.size)]
    used += third
    val thirdOct = if (third == Voice.RECORDER || third == Voice.PANPIPES) 1 else 0
    // Panpipes are nearly all breath and no fundamental, so at a shared gain they vanish under
    // anything with a reed or a string on it — which is why they have never been heard in play
    // despite being in three separate pools. They get their own allowance wherever they land.
    a += VoiceAssignment(third, LineRef.MELODY_ORN, 6, 99, gThird * pipeBoost(third), 0.6f, octave = thirdOct)

    // L7 pads: organ organum OR brass pair
    if (rng.nextBoolean()) {
        a += VoiceAssignment(Voice.ORGAN, LineRef.PADS_FULL, 7, 99, gPads, 0.15f)
    } else {
        a += VoiceAssignment(Voice.SACKBUT, LineRef.PADS_ROOT, 7, 99, gPads, -0.15f)
        a += VoiceAssignment(Voice.HORN, LineRef.PADS_FIFTH, 7, 99, gPads * 0.8f, 0.15f)
    }

    // L8 percussion II — ONE voice keeps the subdivision, drawn per song.
    //
    // Both the shaker and the tambourine used to be assigned here unconditionally, so every single
    // run had both of them and the shaker in particular stopped being a colour and became the
    // texture. The job is now filled once, by whichever of four instruments this song drew.
    val lightPerc = weightedPick(rng, listOf(
        Voice.EGG_SHAKER to 0.28f,
        Voice.TAMBOURINE to 0.28f,
        Voice.BODHRAN to 0.24f,     // a hand drum working the same interlocking figure
        Voice.TIMPANI to 0.20f      // tuned kettledrums, DUM-dum on two different notes
    ))
    a += VoiceAssignment(
        lightPerc, LineRef.PERC_LIGHT, if (merrier) 5 else 7, 99,
        gPerc2 * (if (lightPerc == Voice.TIMPANI) 0.9f else 0.85f),
        if (lightPerc == Voice.TAMBOURINE) 0.65f else -0.6f
    )
    // Even outside the brawl a bass drum under the downbeats gives the consort a floor.
    a += VoiceAssignment(Voice.KICK, LineRef.PERC, 6, 99, gPerc1 * 0.8f, 0f)

    // L9 waits band
    val shawmLevel = if (wilder) 7 else 9
    a += VoiceAssignment(Voice.SHAWM, LineRef.MELODY, shawmLevel, 99, gWaits, -0.35f)
    if (bass != Voice.SACKBUT) a += VoiceAssignment(Voice.SACKBUT, LineRef.BASS, 9, 99, gBass * 0.6f, -0.3f, octave = 0)

    // A brass SECTION — first, second and third parts, not one trumpet played louder.
    //
    // All three take the same call and sit a diatonic third apart, which is what transposeDegrees
    // does: -2 and -4 steps are a third and a fifth below IN THE MODE, so the harmony can never
    // leave the key however the call moves. Spread across the stereo field so they read as three
    // players standing apart rather than one thickened line, and the tuba takes the ground under
    // them. Occasional: a section that turned out for every run would stop being an event.
    if (rng.nextFloat() < 0.35f) {
        a += VoiceAssignment(Voice.HORN, LineRef.TRUMPETER, 9, 99, gWaits * 0.55f, -0.3f)
        a += VoiceAssignment(Voice.HORN, LineRef.TRUMPETER, 9, 99, gWaits * 0.44f, 0f, transposeDegrees = -2)
        a += VoiceAssignment(Voice.SACKBUT, LineRef.TRUMPETER, 10, 99, gWaits * 0.38f, 0.3f, transposeDegrees = -4)
        a += VoiceAssignment(Voice.TUBA, LineRef.BASS, 9, 99, gBass * 0.55f, -0.1f)
    } else {
        // Even without the section, the tuba turns up late — it is the bottom of the band.
        a += VoiceAssignment(Voice.TUBA, LineRef.BASS, 12, 99, gBass * 0.42f, -0.1f)
    }

    // L10 bells + psaltery sparkle
    a += VoiceAssignment(Voice.BELLS, LineRef.SPARKLE, 10, 99, gBells, 0.8f)
    if (Voice.PSALTERY !in used) a += VoiceAssignment(Voice.PSALTERY, LineRef.SPARKLE, 10, 99, gSparkle, 0.45f)

    // L11 timpani
    val timpLevel = if (nobler) 10 else 11
    a += VoiceAssignment(Voice.TIMPANI, LineRef.PERC, timpLevel, 99, gTimp, 0f)

    // The choir was reachable ONLY through the bespoke BRAWL and THRONE plans, so an ordinary run
    // never heard it however deep it went. Uncommon and late and quiet here — voices entering over
    // a consort is a large gesture, and it should stay a surprise rather than become the texture.
    if (rng.nextFloat() < 0.22f) {
        a += VoiceAssignment(Voice.CHOIR, LineRef.PADS_FULL, 14, 99, gPads * 0.75f, 0.05f)
    }

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
    a += VoiceAssignment(Voice.OBOE, LineRef.COUNTER, 14, 99, 0.17f, 0.45f)
    a += VoiceAssignment(Voice.FIDDLE2, LineRef.MELODY, 15, 99, 0.18f, -0.55f, transposeDegrees = -5)
    a += VoiceAssignment(Voice.CELLO, LineRef.BASS, 16, 99, 0.20f, -0.35f, octave = -1)
    a += VoiceAssignment(Voice.VIOLA, LineRef.COUNTER, 17, 99, 0.16f, -0.4f, octave = -1)
    a += VoiceAssignment(soloist, LineRef.MELODY, 19, 99, 0.16f, 0.1f, octave = 1)
    a += VoiceAssignment(Voice.FIDDLE2, LineRef.DRONE, 21, 99, 0.10f, 0.75f, octave = 1)

    return OrchestrationPlan(a, destinyFanfare)
}

/** BRAWL: a cumulative medieval speed-metal arrangement with sparse upper-level flourishes. */
private fun planBrawlOrchestration(spec: SongSpec, hasTrumpeter: Boolean, rng: Random): OrchestrationPlan {
    val a = mutableListOf<VoiceAssignment>()

    // The shawm takes the tune. A loud double reed cuts through a wall of drums the way an
    // arcade lead synth does; the harp lead that briefly replaced it read as a plucked melody
    // sitting inside the mix rather than a lead line riding on top of it.
    val lead = Voice.SHAWM
    a += VoiceAssignment(lead, LineRef.MELODY, 1, 99, 0.46f, 0f)

    // Everything from here rolls per run. The opening line-up was a fixed seven voices, so every
    // brawl in every run began with the identical wall of sound and they all blurred together.
    val riff = weightedPick(rng, listOf(Voice.LUTE to 0.5f, Voice.GURDY to 0.3f, Voice.PSALTERY to 0.2f))
    a += VoiceAssignment(riff, LineRef.RIFF, 1, 99, 0.38f, -0.3f)

    // Who answers the lead, and whether they open alongside it or arrive a level or two in.
    // Never the lead's own voice, or the answer stops reading as an answer.
    val foil = weightedPick(rng, listOf(Voice.SHAWM to 0.45f, Voice.VIELLE to 0.3f, Voice.FIDDLE2 to 0.25f)
        .filter { it.first != lead })
    a += VoiceAssignment(foil, LineRef.COUNTER, 1 + rng.nextInt(3), 99, 0.34f, 0.4f)

    // Kit: kick and snare are fixed — it is metal — but the third drum and its entry vary.
    // Centre-panned and the loudest thing in the kit, as a kick drum is.
    a += VoiceAssignment(Voice.KICK, LineRef.PERC, 1, 99, 0.42f, 0f)       // double kick
    a += VoiceAssignment(Voice.TABOR, LineRef.PERC, 1, 99, 0.50f, -0.45f)  // snare
    a += VoiceAssignment(Voice.NAKERS, LineRef.PERC, 2, 99, 0.34f, 0.4f)   // tom accents
    val thirdDrum = weightedPick(rng, listOf(Voice.BODHRAN to 0.6f, Voice.TAMBOURINE to 0.4f))
    a += VoiceAssignment(thirdDrum, LineRef.PERC, 1 + rng.nextInt(2), 99, 0.24f, -0.55f)

    // The drone bed. Never the same voice as the riff, or the low end turns to porridge.
    // The drone bed. The organ was here on 40% of brawls AND again on PADS_FULL later, so it was
    // the commonest thing in the mode — and as a bare drone it only ever sounded two notes, which
    // is why it read as an organ playing "a couple of chord notes" rather than as an organ. It is
    // rarer now, and when it does turn up it gets the full chord on top of the bed.
    val drone = weightedPick(
        rng,
        listOf(Voice.GURDY to 0.78f, Voice.ORGAN to 0.22f).filter { it.first != riff }
            .ifEmpty { listOf(Voice.GURDY to 1f) }
    )
    a += VoiceAssignment(drone, LineRef.DRONE, 1, 99, 0.36f, -0.6f)
    if (drone == Voice.ORGAN) {
        // The drone bed alone is two notes, which is precisely what it sounded like. Give the
        // organ the whole chord as well and it reads as a plenum — the only reason to put one in
        // a brawl in the first place.
        a += VoiceAssignment(Voice.ORGAN, LineRef.PADS_FULL, 1, 99, 0.26f, -0.5f)
    } else {
        // GURDY+SPARKLE is the buzz-accent mapping in the composer facade, not an arpeggio.
        a += VoiceAssignment(Voice.GURDY, LineRef.SPARKLE, 1, 99, 0.22f, -0.6f)
    }

    a += VoiceAssignment(Voice.VIOLA, LineRef.BASS, 2, 99, 0.32f, -0.2f)
    a += VoiceAssignment(Voice.RECORDER, LineRef.FLOURISH, 2, 99, 0.12f, 0.55f)
    if (hasTrumpeter) a += VoiceAssignment(Voice.HORN, LineRef.TRUMPETER, 2, 99, 0.16f, 0.3f)

    a += VoiceAssignment(Voice.SACKBUT, LineRef.PADS_ROOT, 3, 99, 0.18f, -0.1f)
    a += VoiceAssignment(Voice.HORN, LineRef.PADS_FIFTH, 3, 99, 0.15f, 0.1f)
    a += VoiceAssignment(Voice.HORN, LineRef.FLOURISH, 3, 99, 0.18f, 0.25f)

    if (thirdDrum != Voice.TAMBOURINE) a += VoiceAssignment(Voice.TAMBOURINE, LineRef.PERC, 4, 99, 0.15f, 0.65f)
    a += VoiceAssignment(Voice.BELLS, LineRef.SPARKLE, 5, 99, 0.10f, 0.7f)
    a += VoiceAssignment(Voice.TIMPANI, LineRef.PERC, 5, 99, 0.28f, 0f)
    if (riff != Voice.PSALTERY) a += VoiceAssignment(Voice.PSALTERY, LineRef.RIFF, 6, 99, 0.10f, 0.45f, octave = 1)
    // Rolled chords punctuating its own riff — in most brawls, but not all of them.
    if (rng.nextFloat() < 0.7f) {
        a += VoiceAssignment(Voice.HARP, LineRef.STRUM, 3 + rng.nextInt(3), 99, 0.26f, 0.15f)
    }
    // The harp doubles the tune's divisions an octave up — a plucked shimmer over the lead.
    a += VoiceAssignment(Voice.HARP, LineRef.MELODY_ORN, 7, 99, 0.18f, 0.25f, octave = 1)
    a += VoiceAssignment(Voice.PANPIPES, LineRef.FLOURISH, 8, 99, 0.11f * pipeBoost(Voice.PANPIPES), 0.6f)
    // A tuba doubling the chord roots is exactly the joke a brass band makes of a metal riff.
    a += VoiceAssignment(Voice.TUBA, LineRef.PADS_ROOT, 9, 99, 0.17f, -0.35f)
    // Only if it is not already the drone-bed organ above — it was possible to field two.
    if (drone != Voice.ORGAN) a += VoiceAssignment(Voice.ORGAN, LineRef.PADS_FULL, 10, 99, 0.08f, 0f)
    a += VoiceAssignment(Voice.CHOIR, LineRef.FLOURISH, 11, 99, 0.10f, 0.2f)

    // Late consort: whatever the rolls above left out joins for the closing stretch, quietly.
    // The variation is meant to change how a brawl OPENS, not to cost a long run voices — a
    // deep run should still end up hearing the whole palette.
    val used = a.map { it.voice }.toSet()
    var lateLevel = 12
    fun late(v: Voice, line: LineRef, gain: Float, pan: Float, octave: Int = 0) {
        if (v in used) return
        a += VoiceAssignment(v, line, lateLevel, 99, gain, pan, octave = octave)
        lateLevel++
    }
    late(Voice.LUTE, LineRef.RIFF, 0.16f, -0.45f)
    late(Voice.GURDY, LineRef.DRONE, 0.14f, -0.7f)
    late(Voice.BODHRAN, LineRef.PERC, 0.16f, -0.5f)
    late(Voice.SHAWM, LineRef.MELODY, 0.16f, -0.35f)
    late(Voice.VIELLE, LineRef.COUNTER, 0.14f, 0.35f)
    late(Voice.FIDDLE2, LineRef.MELODY_ORN, 0.14f, 0.4f)
    late(Voice.PSALTERY, LineRef.SPARKLE, 0.10f, 0.5f)
    late(Voice.TAMBOURINE, LineRef.PERC, 0.13f, 0.65f)
    late(Voice.OBOE, LineRef.COUNTER, 0.15f, 0.45f)
    late(Voice.CELLO, LineRef.BASS, 0.18f, -0.35f)
    late(Voice.EGG_SHAKER, LineRef.PERC, 0.14f, -0.6f)
    late(Voice.KICK, LineRef.PERC, 0.30f, 0f)
    return OrchestrationPlan(a, destinyFanfare = false)
}

/**
 * THRONE: a royal processional — regal and majestic, not oppressive. The melody leads from the
 * first bar over stately timpani; herald horns answer in fanfares; the choir lifts rather than
 * looms; bells crown the cadences. The old plan (choir menace, gurdy drone, melody withheld until
 * L4) read as a dirge.
 */
private fun planThroneOrchestration(spec: SongSpec, hasTrumpeter: Boolean): OrchestrationPlan {
    val a = mutableListOf<VoiceAssignment>()
    a += VoiceAssignment(Voice.VIELLE, LineRef.MELODY, 1, 99, 0.40f, 0.1f, octave = 1)
    a += VoiceAssignment(Voice.HARP, LineRef.ACCOMP, 1, 99, 0.24f, -0.35f)
    a += VoiceAssignment(Voice.VIOLA, LineRef.BASS, 1, 99, 0.32f, -0.3f)
    a += VoiceAssignment(Voice.TIMPANI, LineRef.PERC, 1, 99, 0.52f, 0f)
    a += VoiceAssignment(Voice.BELLS, LineRef.SPARKLE, 1, 99, 0.30f, 0.6f)
    // Herald fanfares: rising horn triads every four bars (the same figure that crowns BRAWL)
    a += VoiceAssignment(Voice.HORN, LineRef.FLOURISH, 1, 99, 0.38f, 0.2f)
    a += VoiceAssignment(Voice.SACKBUT, LineRef.PADS_ROOT, 2, 99, 0.22f, -0.15f)
    a += VoiceAssignment(Voice.HORN, LineRef.PADS_FIFTH, 2, 99, 0.24f, 0.15f)
    // A coronation has heralds whether or not you have recruited one, so the trumpet line plays
    // regardless here — the Trumpeter ancillary below makes it louder and adds a second part,
    // rather than being the only thing that lets a trumpet into the room at all.
    a += VoiceAssignment(Voice.HORN, LineRef.TRUMPETER, 1, 99, 0.30f, -0.25f)
    a += VoiceAssignment(Voice.CHOIR, LineRef.PADS_FULL, 4, 99, 0.14f, 0f)
    // Harp rolls across the cadences — a court harper's gesture, and it suits a coronation.
    a += VoiceAssignment(Voice.HARP, LineRef.STRUM, 3, 99, 0.28f, -0.2f)
    a += VoiceAssignment(Voice.RECORDER, LineRef.MELODY_ORN, 5, 99, 0.18f, 0.5f, octave = 1)
    a += VoiceAssignment(Voice.PSALTERY, LineRef.SPARKLE, 6, 99, 0.14f, 0.45f)
    // A cello under the processional and an oboe answering over it — the court consort proper.
    a += VoiceAssignment(Voice.CELLO, LineRef.BASS, 5, 99, 0.26f, -0.4f)
    // The bottom of a processional band. A coronation walks on the tuba's note.
    a += VoiceAssignment(Voice.TUBA, LineRef.BASS, 6, 99, 0.30f, -0.15f)
    a += VoiceAssignment(Voice.OBOE, LineRef.COUNTER, 6, 99, 0.20f, 0.4f)
    a += VoiceAssignment(Voice.CHOIR, LineRef.COUNTER, 7, 99, 0.16f, 0.35f)
    a += VoiceAssignment(Voice.ORGAN, LineRef.PADS_FULL, 9, 99, 0.12f, 0f)
    // Your own trumpeter doubles the heralds an octave up — prevalent, as a throne should be.
    if (hasTrumpeter) a += VoiceAssignment(Voice.HORN, LineRef.TRUMPETER, 1, 99, 0.26f, 0.35f, octave = 1)
    // Deep runs earn the full coronation: massed fanfare on the strain-ends.
    a += VoiceAssignment(Voice.HORN, LineRef.DESTINY_FANFARE, 10, 99, 0.28f, -0.2f)
    a += VoiceAssignment(Voice.BELLS, LineRef.DESTINY_FANFARE, 10, 99, 0.20f, 0.7f)
    return OrchestrationPlan(a, destinyFanfare = true)
}

// ---- Pattern generators (all pure functions of the spec - level-free, rng-free) ----

/**
 * The brawl's bass drum figure, one per song off the seed. A continuous sixteenth double pedal
 * is a 130bpm pattern; at the 192-219 this family now runs it stops reading as two feet and
 * becomes a buzz, so the kick drives and the double pedal is saved for the runs above.
 */
private fun brawlKickFigure(spec: SongSpec): List<Float> =
    when (Random(spec.seed xor 0x4B1C4L).nextInt(4)) {
        0 -> listOf(0f, 1f, 2f, 3f)                                  // four on the floor
        1 -> listOf(0f, 1f, 1.75f, 2f, 3f)                           // pushed into beat 3
        2 -> listOf(0f, 0.5f, 0.75f, 1f, 2f, 2.5f, 2.75f, 3f)        // the metal gallop
        else -> listOf(0f, 1f, 2f, 2.75f, 3.5f)                      // leans over the barline
    }

/**
 * What the bass drum plays this bar, as (beat, velocity) pairs.
 *
 * One figure per song off the seed, plus a variation on the fourth bar — the same trick the brawl
 * kick uses, dialled right down. A drummer keeping time for a consort still doubles a beat, still
 * pushes into the next bar; what he does not do is play sixteenths. The point is that two runs
 * should not share a pulse, and that the fourth bar should tell you where you are in the phrase.
 */
private fun plainKickFigure(spec: SongSpec, bar: Int): List<Pair<Float, Float>> {
    val bpb = spec.beatsPerBar
    val figure = Random(spec.seed xor 0x8A55D2058L).nextInt(4)
    val half = bpb / 2f
    val base = when {
        bpb == 3 -> when (figure) {                       // 3/4: the downbeat is the whole story
            0 -> listOf(0f to 0.9f)
            1 -> listOf(0f to 0.9f, 2f to 0.6f)
            2 -> listOf(0f to 0.9f, 1.5f to 0.55f)
            else -> listOf(0f to 0.9f, 0.5f to 0.5f)      // the quick double off the beat
        }
        bpb == 6 -> when (figure) {                       // 6/8: the two compound beats
            0 -> listOf(0f to 0.9f, 3f to 0.7f)
            1 -> listOf(0f to 0.9f, 2.5f to 0.55f, 3f to 0.7f)
            2 -> listOf(0f to 0.9f, 3f to 0.7f, 5f to 0.5f)
            else -> listOf(0f to 0.9f, 1.5f to 0.5f, 3f to 0.7f)
        }
        else -> when (figure) {                           // duple
            0 -> listOf(0f to 0.9f, half to 0.7f)
            1 -> listOf(0f to 0.9f, half to 0.7f, half + 0.5f to 0.5f)  // the double
            2 -> listOf(0f to 0.9f, 1.5f to 0.55f, half to 0.7f)        // dotted, a slight limp
            else -> listOf(0f to 0.9f, half to 0.7f, bpb - 0.5f to 0.5f) // pickup into the next bar
        }
    }
    // Fourth bar of the phrase: an extra push, so the four-bar shape is audible in the floor of
    // the mix rather than only in the tune. Skipped if the figure already hits there — a drum
    // retriggered on top of itself is a click, not an accent.
    if (bar % 4 != 3) return base
    val push = bpb - 0.5f
    return if (base.any { Math.abs(it.first - push) < 1e-3f }) base else base + listOf(push to 0.62f)
}

/**
 * The subdivision line, played by whichever instrument this song gave the job to.
 *
 * All four play the SAME musical role — the offbeat pulse the melody is felt against — but each
 * plays it the way its own instrument would, which is the point: a hand drum cannot rattle
 * continuous quavers the way a shaker can, and a pair of kettledrums answers in pitch rather than
 * in noise. Handing one figure to four voices is what makes two runs of the same family sound
 * like different bands rather than the same band with a knob turned.
 */
fun lightPercussionEvents(spec: SongSpec, voice: Voice, wilder: Boolean): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    // Kettledrums are tuned to the tonic and the dominant, which is the whole reason a timpani
    // figure reads as DUM-dum and not as one drum hit twice.
    val low = spec.finalMidi - 12
    val high = degreeToMidi(spec, 4) - 12
    // Which DUM-dum this song plays, off the seed: the pairs a timpanist actually alternates.
    val figure = Random(spec.seed xor 0x71DEC0DEL).nextInt(3)

    for (bar in 0 until spec.totalBars) {
        val base = bar * bpb
        when (voice) {
            Voice.TAMBOURINE -> {
                // Jingles on the offbeats, thumb on the beat — the way it is actually held.
                var beat = 0f
                while (beat < bpb - 1e-3f) {
                    val onBeat = beat % 1f == 0f
                    out += NoteEvent(base + beat, if (onBeat) 0.16f else 0.28f, 57, if (onBeat) 0.5f else 0.85f)
                    beat += 0.5f
                }
            }
            Voice.BODHRAN, Voice.TABOR -> {
                // The same interlocking figure, but a hand drum's version of it: the offbeats it
                // can actually articulate, not a continuous rattle it would only smear.
                val offs = if (spec.beatsPerBar == 6) listOf(0f, 1.5f, 3f, 4.5f) else listOf(0f, 1.5f, 2.5f)
                for ((i, o) in offs.withIndex()) {
                    if (o >= bpb - 1e-3f) continue
                    out += NoteEvent(base + o, 0.22f, if (i == 0) 50 else 57, if (i == 0) 0.85f else 0.6f)
                }
                if (wilder && bpb >= 4f) out += NoteEvent(base + bpb - 0.5f, 0.18f, 57, 0.5f)
            }
            Voice.TIMPANI -> {
                // DUM dum. Two drums, two pitches, and a different pattern per song.
                //
                // Durations are long on purpose. A kettledrum is not a hit, it is a hit followed
                // by a boom, and the boom is the instrument — write these short and what comes out
                // is a tuned tap. They overlap the next note deliberately; that ringing-into-each
                // -other is what a pair of timpani actually sounds like.
                when (figure) {
                    0 -> {  // heavy-light on the tonic then the fifth
                        out += NoteEvent(base, 1.8f, low, 1f)
                        out += NoteEvent(base + 1f, 1.4f, high, 0.62f)
                    }
                    1 -> {  // the answer falls in the second half of the bar
                        out += NoteEvent(base, 2.0f, low, 1f)
                        out += NoteEvent(base + (if (bpb >= 4f) 2f else bpb - 1f), 1.6f, high, 0.7f)
                    }
                    else -> { // dotted: DUM . dum-dum, alternating drums
                        out += NoteEvent(base, 1.6f, low, 1f)
                        out += NoteEvent(base + 1.5f, 1.2f, high, 0.66f)
                        if (bpb >= 4f) out += NoteEvent(base + 2.5f, 1.2f, low, 0.58f)
                    }
                }
                // The roll. Every fourth bar the pair go into a tremolo and crescendo into the
                // downbeat of the next — the big rolling drum a kettledrum is FOR, and the thing
                // a bar-by-bar figure on its own can never produce.
                if (bar % 4 == 3) {
                    val strokes = 12
                    val from = bpb - 2f
                    for (r in 0 until strokes) {
                        val t = r.toFloat() / (strokes - 1)
                        out += NoteEvent(
                            base + from + t * 2f, 0.5f,
                            if (r % 2 == 0) low else high,
                            0.32f + 0.55f * t          // swelling into the barline
                        )
                    }
                }
            }
            else -> {   // EGG_SHAKER, and the fallback for anything else handed this line
                var beat = 0f
                while (beat < bpb - 1e-3f) {
                    val onBeat = beat % 1f == 0f
                    out += NoteEvent(base + beat, 0.18f, 57, if (onBeat) 0.5f else 0.8f)
                    beat += 0.5f
                }
            }
        }
    }
    return out
}

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
            // The shaker keeps the subdivision the drums cannot: it is all treble, so it stays
            // legible on a phone under a full mix. Offbeats, so it interlocks with the tabor
            // rather than doubling it.
            Voice.EGG_SHAKER -> {
                val step = if (spec.beatsPerBar == 6) 0.5f else 0.5f
                var beat = 0f
                while (beat < bpb - 1e-3f) {
                    val onBeat = beat % 1f == 0f
                    out += NoteEvent(base + beat, 0.18f, 57, if (onBeat) 0.5f else 0.8f)
                    beat += step
                }
            }
            // The double kick proper, on an actual bass drum. This pattern used to be on NAKERS,
            // which is a 150-200Hz kettle drum — it could play the rhythm but never sound like a
            // kick, so the theme had a busy tom where its engine should be.
            Voice.KICK -> if (spec.family == Family.BRAWL) {
                // A continuous sixteenth double pedal is a 130bpm figure. Wound up past that it
                // stops reading as two feet and turns into a buzz, so the faster patterns play a
                // figure and save the double pedal for a run at the end of every fourth bar.
                // Durations stay at 0.30: 0.14 was shorter than the drum's own decay, so every
                // hit was cut off mid-body and only the attack survived. That is the rattle.
                val beats = brawlKickFigure(spec).filter { it < bpb - 1e-3f }
                for (b in beats) {
                    out += NoteEvent(base + b, 0.30f, 36, when {
                        b % 2f == 0f -> 1f
                        b % 1f == 0f -> 0.8f
                        else -> 0.55f
                    })
                }
                // The occasional double-pedal run, over the last beat of every fourth bar. Hits
                // the figure already plays are skipped, or the drum retriggers on top of itself.
                if (bar % 4 == 3) {
                    for (r in 0 until 4) {
                        val b = bpb - 1f + r * 0.25f
                        if (beats.none { Math.abs(it - b) < 1e-3f }) {
                            out += NoteEvent(base + b, 0.30f, 36, 0.7f + 0.08f * r)
                        }
                    }
                }
            } else {
                // Outside the brawl the bass drum used to be a metronome: one hit on the downbeat,
                // one at the half bar, every bar of every song. Correct and completely inert. It
                // now draws a figure per song — a double, a pickup into the next bar, a dotted
                // limp — and varies it on the fourth bar so the pattern is felt as a pattern.
                // Nothing like as busy as the brawl's double pedal; a consort's drummer is keeping
                // time, not driving. He is just allowed to be a person about it.
                for ((b, v) in plainKickFigure(spec, bar)) {
                    if (b < bpb - 1e-3f) out += NoteEvent(base + b, 0.2f, 36, v)
                }
            }
            Voice.NAKERS -> if (spec.family == Family.BRAWL) {
                // Freed from double-kick duty: now a tom accent answering the snare on the turn.
                if (bar % 2 == 1) {
                    out += NoteEvent(base + bpb - 1.5f, 0.22f, 56, 0.85f)
                    out += NoteEvent(base + bpb - 1.0f, 0.22f, 57, 0.75f)
                    out += NoteEvent(base + bpb - 0.5f, 0.22f, 56, 0.9f)
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

/**
 * Where in a four-bar phrase a given song's harper places their rolled chords. Rolled once per
 * song so two runs of the same family still strum differently — the harper has a habit, and it
 * is not the same habit every time.
 */
enum class StrumPlacement {
    /** Under the cadence, where the melody is already resting. The courtly default. */
    PHRASE_END,
    /** On the downbeat, announcing the phrase before the tune enters over the top. */
    PHRASE_START,
    /** One bar in — answering the melody's opening gesture rather than setting it up. */
    SECOND_BAR,
    /** Bookends: opens the strain and closes it, nothing in between. */
    ANSWER
}

/** The harper's habit for this song. Deterministic per seed, so a run's theme stays its own. */
fun strumPlacementFor(spec: SongSpec): StrumPlacement =
    StrumPlacement.values()[Random(spec.seed xor 0x51EA3F17L).nextInt(StrumPlacement.values().size)]

/**
 * Rolled chords for the harp.
 *
 * Every pitch comes from degreeToMidi() against the bar's own ground degree, so the voicing is
 * built out of the mode rather than transposed into it — a strum cannot come out discordant
 * whatever the placement, and the third is minor in aeolian/dorian and major in ionian without
 * anything asking which. (groundChordMidis is deliberately not used: it gives root-fifth-octave,
 * no third at all, which strums as a hollow power chord.)
 *
 * Placement, roll speed and spread are all drawn from the song's own seed, so two GREENSLEEVES
 * runs strum in different places. The roll is converted from milliseconds into beats so it stays
 * a hand dragged across the strings at any tempo instead of stretching into an arpeggio at slow
 * ones. Whether a song strums *at all* is decided in the orchestration plan, not here.
 */
fun harpStrumEvents(spec: SongSpec): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    val rng = Random(spec.seed xor 0x7C1D9A03L)
    val placement = strumPlacementFor(spec)
    // 26-46ms between strings: a brisk flick or a broad sweep, depending on the run.
    val rollBeats = ((0.026f + rng.nextFloat() * 0.020f) / spec.secondsPerBeat).coerceIn(0.015f, 0.11f)
    val wide = rng.nextBoolean()   // does the big roll reach up to the tenth, or stop at the octave

    for (bar in 0 until spec.totalBars) {
        val inPhrase = bar % 4
        val hit = when (placement) {
            StrumPlacement.PHRASE_END -> inPhrase == 3
            StrumPlacement.PHRASE_START -> inPhrase == 0
            StrumPlacement.SECOND_BAR -> inPhrase == 1
            StrumPlacement.ANSWER -> bar % 8 == 0 || bar % 8 == 7
        }
        if (!hit) continue

        // The strain's closing bar is the one that earns the full spread, wherever the habit puts
        // the rest of them.
        val big = bar % 8 == 7 || (placement == StrumPlacement.PHRASE_START && bar % 8 == 0)
        val g = spec.ground[bar % 8].bassDegree
        // Any step that comes out a tritone above the root is dropped rather than rolled — see
        // strumDegrees. On vii in ionian and iv in phrygian that is the fifth AND the twelfth, and
        // two poisoned strings out of six is the "weird discordant noise".
        val degrees = strumDegrees(spec, g, when {
            big && wide -> listOf(0, 2, 4, 7, 9, 11)
            big -> listOf(0, 2, 4, 7, 9)
            else -> listOf(0, 2, 4, 7)
        })
        // Cadential strums sit late in the bar; announcing ones sit on the downbeat.
        val start = when (placement) {
            StrumPlacement.PHRASE_END -> bar * bpb + bpb - (if (big) 1.5f else 1f)
            StrumPlacement.ANSWER -> if (bar % 8 == 7) bar * bpb + bpb - 1.5f else bar * bpb
            else -> bar * bpb
        }
        // Ring to the end of its own bar, unless the next bar is the same chord anyway.
        //
        // Measured, not guessed: the roll SPEED was never the problem — 26-44ms between strings is
        // exactly what a harp does. What was wrong is how long the strings were left ringing. A big
        // strum ran 1.2-1.6 beats past its own barline, and in half the songs the bar it ran into
        // was a different chord, so the old harmony sat under the new one. That is the "slightly
        // discordant", and the melody rubbing against the stale stack is the audible half of it.
        // Where the ground does not change under it, the long ring is harmless and stays — a
        // cadential strum into the tonic should be allowed to bloom. The harp's own release tail
        // (renderEvents) still carries every roll over the barline naturally.
        val nextIsSameChord = g == spec.ground[(bar + 1) % 8].bassDegree
        val barRemaining = bpb - (start - bar * bpb)
        val ring = (if (big) 2.6f else 1.4f).let { if (nextIsSameChord) it else it.coerceAtMost(barRemaining) }
        val lead = if (big) 0.92f else 0.68f
        degrees.forEachIndexed { i, d ->
            // Velocity eases off up the roll: the thumb hits hardest, as on a real harp.
            out += NoteEvent(start + i * rollBeats, ring, degreeToMidi(spec, g + d), lead - 0.035f * i)
        }
    }
    return out
}

/**
 * What the brass does with itself, chosen once per song off the run seed.
 *
 * It used to be one fixed three-note fanfare at the end of every eight bars, in every song, for
 * every run — which is most of why the pieces sounded like each other. Brass that always doubles
 * the tune has nothing to say; these are three genuinely different jobs it can be given.
 */
private enum class BrassRole { CADENCE_CALL, LABOURING_OVER, THREE_PART }

fun trumpeterEvents(spec: SongSpec): List<NoteEvent> {
    val out = mutableListOf<NoteEvent>()
    val bpb = spec.beatsPerBar.toFloat()
    val strainBars = 8
    val rng = Random(spec.seed xor 0x7B12A5C3L)
    val role = BrassRole.values()[rng.nextInt(BrassRole.values().size)]
    // Modal third, so a minor-mode piece does not get a cheerful major triad bolted on top.
    val third = spec.mode.steps[2]

    when (role) {
        // The original: a short call announcing the end of each strain.
        BrassRole.CADENCE_CALL -> {
            var bar = strainBars - 1
            while (bar < spec.totalBars) {
                val base = bar * bpb
                out += NoteEvent(base, 0.5f, spec.finalMidi + 12, 0.9f)
                out += NoteEvent(base + 0.5f, 0.5f, spec.finalMidi + 19, 0.9f)
                out += NoteEvent(base + 1f, 1f, spec.finalMidi + 24, 1f)
                bar += strainBars
            }
        }
        // Long tones lying ACROSS the tune rather than moving with it — the brass leaning on the
        // piece from above while the melody carries on underneath.
        BrassRole.LABOURING_OVER -> {
            var bar = 2
            while (bar < spec.totalBars) {
                val base = bar * bpb
                // Root, then the fifth two bars later: slow enough that it reads as weight, not
                // as a counter-melody competing with the tune.
                val g = spec.ground[bar % 8].bassDegree
                out += NoteEvent(base, bpb * 2f - 0.2f, degreeToMidi(spec, g) + 12, 0.7f)
                if (bar + 2 < spec.totalBars) {
                    out += NoteEvent(base + bpb * 2f, bpb * 2f - 0.2f, degreeToMidi(spec, g + 4) + 12, 0.62f)
                }
                bar += 4
            }
        }
        // First, second and third trumpet: a stacked chord, entering one after the other so you
        // hear it BUILD rather than arriving as a block.
        BrassRole.THREE_PART -> {
            var bar = strainBars - 2
            while (bar < spec.totalBars) {
                val base = bar * bpb
                val g = spec.ground[bar % 8].bassDegree
                val root = degreeToMidi(spec, g) + 12
                // 1st on top, 2nd on the modal third, 3rd on the fifth below it.
                listOf(root + 12, root + third, root).forEachIndexed { part, midi ->
                    out += NoteEvent(
                        base + part * 0.35f,
                        bpb * 2f - part * 0.35f - 0.15f,
                        midi,
                        0.85f - part * 0.08f
                    )
                }
                bar += strainBars
            }
        }
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
