package com.example.game

import kotlin.random.Random

internal fun renderBowBrass(
    voice: Voice,
    midi: Int,
    durSec: Float,
    velocity: Float,
    sr: Int,
    rng: Random,
    phraseIndex: Int
): FloatArray = when (voice) {
    // Brighter than a viola on purpose: a gut-strung vielle IS nasal and reedy, and at slope 1.0
    // it measured as putting more energy below 300Hz than above it — inaudible on a handset.
    Voice.VIELLE  -> bowed(midi, durSec, sr, rng, bodyHz = floatArrayOf(340f, 850f, 1750f), vibHz = 5.0f, detuneCents = 0f, attack = 0.07f, harmonicSlope = 0.82, bowNoise = 0.07f)
    // Slope was 1.3 — so dark that more of its energy sat below 300Hz than above, which on a phone
    // speaker is silence. Now mid-focused: darker than the vielle, brighter than the cello, and
    // its body bands sit between theirs so all three stay tellable apart.
    Voice.VIOLA   -> bowed(midi, durSec, sr, rng, bodyHz = floatArrayOf(280f, 720f, 1400f), vibHz = 4.3f, detuneCents = 0f, attack = 0.13f, harmonicSlope = 0.95, bowNoise = 0.035f)
    // Cello. Its real fundamental lives below anything a phone speaker can move, so the weight has
    // to be carried by the harmonic series instead — a shallow slope keeps the upper partials
    // strong and the ear reconstructs the missing fundamental from them. The 2.2kHz body band is
    // the cello's "bridge hill", the resonance that survives on a handset and tells it from VIOLA.
    Voice.CELLO   -> bowed(midi, durSec, sr, rng, bodyHz = floatArrayOf(250f, 900f, 2600f), vibHz = 4.6f, detuneCents = 0f, attack = 0.16f, harmonicSlope = 0.58, bowNoise = 0.055f)
    Voice.FIDDLE2 -> bowed(midi, durSec, sr, rng, bodyHz = floatArrayOf(420f, 1050f, 2200f), vibHz = 5.8f, detuneCents = 4f, attack = 0.04f, harmonicSlope = 0.82, bowNoise = 0.11f)
    Voice.SHAWM   -> shawm(midi, durSec, sr, rng)
    // Both were voiced too dark to survive a phone speaker — under a third of their energy sat
    // above 300Hz. Real brass is bright: it is the upper partials that make it read as brass at
    // all, and they are the only part a handset reproduces.
    Voice.SACKBUT -> brass(midi, durSec, sr, rng, brightness = 1.7f, attack = 0.045f, cutoffHz = 4600f, lipNoise = 0.025f)
    Voice.HORN    -> brass(midi, durSec, sr, rng, brightness = 1.2f, attack = 0.11f, cutoffHz = 2900f, lipNoise = 0.01f)
    // Tuba. Same instrument family, different bore: a slow lip, a dark cutoff, and a broad
    // attack. Brightness stays up despite the dark cutoff for the CELLO reason — the fundamental
    // of a tuba is below anything a handset can move, so what the ear actually receives is the
    // harmonic series, and stripping that leaves silence with a thud on the front of it.
    Voice.TUBA    -> brass(midi, durSec, sr, rng, brightness = 1.45f, attack = 0.085f, cutoffHz = 2100f, lipNoise = 0.03f)
    Voice.GURDY   -> if (durSec < 0.15f) trompette(midi, durSec, sr, rng) else wheelDrone(midi, durSec, sr, rng)
    Voice.ORGAN   -> organ(midi, durSec, sr, rng)
    Voice.CHOIR   -> choir(midi, durSec, sr, rng, phraseIndex)
    else -> FloatArray((sr * durSec).toInt())
}

/** Band-limited saw via additive harmonics, phase-accumulated vibrato, bow noise, body resonances. */
private fun bowed(
    midi: Int,
    durSec: Float,
    sr: Int,
    rng: Random,
    bodyHz: FloatArray,
    vibHz: Float,
    detuneCents: Float,
    attack: Float,
    harmonicSlope: Double,
    bowNoise: Float
): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val f0 = midiHz(midi) * Math.pow(2.0, detuneCents / 1200.0)
    val hCount = minOf(20, (sr * 0.42 / f0).toInt().coerceAtLeast(3))
    val dt = 1.0 / sr
    var phase = 0.0
    val bowLp = OnePoleLP(sr, 2500f)
    val body = bodyHz.map { Biquad.bandpass(sr, it, 2f) }
    for (i in 0 until n) {
        val t = i * dt
        val vib = 8.0 * ((t - 0.3) / 0.4).coerceIn(0.0, 1.0) * Math.sin(2.0 * Math.PI * vibHz * t)
        val f = f0 * Math.pow(2.0, vib / 1200.0)
        phase += 2.0 * Math.PI * f * dt
        var s = 0.0
        for (h in 1..hCount) s += Math.sin(phase * h) / Math.pow(h.toDouble(), harmonicSlope)
        var v = (s * 0.45).toFloat()
        v += bowLp.process(rng.nextFloat() * 2f - 1f) * bowNoise
        var res = 0f; for (b in body) res += b.process(v)
        out[i] = v * 0.5f + res * 0.5f
    }
    applyAR(out, sr, attack, 0.10f)
    normalise(out, 0.9f)
    return out
}

private fun shawm(midi: Int, durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val f0 = midiHz(midi)
    val hCount = minOf(24, (sr * 0.42 / f0).toInt().coerceAtLeast(4))
    val dt = 1.0 / sr; var phase = 0.0
    val bite = Biquad.peakEq(sr, 1500f, 1.2f, 9f)
    for (i in 0 until n) {
        phase += 2.0 * Math.PI * f0 * dt
        var s = 0.0
        for (h in 1..hCount) s += Math.sin(phase * h) / Math.pow(h.toDouble(), 0.8)
        out[i] = bite.process((s * 0.35).toFloat()) + (rng.nextFloat() * 2f - 1f) * 0.02f
    }
    applyAR(out, sr, 0.015f, 0.04f)
    normalise(out, 0.9f)
    return out
}

private fun brass(
    midi: Int,
    durSec: Float,
    sr: Int,
    rng: Random,
    brightness: Float,
    attack: Float,
    cutoffHz: Float,
    lipNoise: Float
): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val f0 = midiHz(midi); val dt = 1.0 / sr; var phase = 0.0
    val toneLp = OnePoleLP(sr, cutoffHz)
    val lipBp = Biquad.bandpass(sr, 900f, 1.2f)
    for (i in 0 until n) {
        val t = i * dt
        phase += 2.0 * Math.PI * f0 * dt
        var s = 0.0
        // Ten partials rather than six: a brass "bloom" is the upper harmonics arriving, and six
        // did not reach high enough for that to be audible on a small speaker.
        for (h in 1..10) {
            val hf = f0 * h; if (hf > sr * 0.45) break
            val swell = (t / (0.04 * h)).coerceAtMost(1.0)   // upper harmonics bloom in - brass swell
            s += swell * Math.sin(phase * h) / Math.pow(h.toDouble(), (1.2 / brightness))
        }
        val tone = toneLp.process((s * 0.4).toFloat())
        out[i] = tone + lipBp.process(rng.nextFloat() * 2f - 1f) * lipNoise
    }
    applyAR(out, sr, attack, 0.12f)
    normalise(out, 0.9f)
    return out
}

/**
 * Hurdy-gurdy wheel: a doubled unison; orchestration supplies the root and modal fifth.
 *
 * Deliberately voiced as the ORGAN's opposite, because the two were blurring into one sustained
 * pad. The organ above is steady, pure-toned and enormous; this is a rosined wheel scraping gut,
 * so everything here is the opposite of steady: a once-around crank flutter that is meant to be
 * plainly audible, hard nasal formants from the boxy body, and a continuous bed of bridge rasp.
 * If you can't tell which is which, widen the wobble depth and rasp below — they are the two
 * traits doing the distinguishing.
 */
private fun wheelDrone(midi: Int, durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val center = midiHz(midi)
    val f1 = center * Math.pow(2.0, -5.0 / 1200.0)
    val f2 = center * Math.pow(2.0, 6.0 / 1200.0)
    val dt = 1.0 / sr; var p1 = 0.0; var p2 = 0.0
    // Dark and narrow, against the organ's bright spread mixture — the two are pushed to opposite
    // ends of the spectrum on purpose, because measured as band energy they were otherwise the
    // same instrument. Keep this low-pass well below the organ's mixture or they re-merge.
    val lp = OnePoleLP(sr, 950f)
    // One tight nasal honk low down, where the organ has comparatively little.
    val nasal1 = Biquad.bandpass(sr, 780f, 7f)
    val nasal2 = Biquad.bandpass(sr, 1150f, 8f)
    for (i in 0 until n) {
        val t = i * dt
        val wob = Math.sin(2.0 * Math.PI * 2.4 * t)      // a faster crank, and a deeper one
        val fm = 1.0 + 0.0075 * wob                      // ±13 cents, integrated so it can't run away
        p1 += 2.0 * Math.PI * f1 * fm * dt; p2 += 2.0 * Math.PI * f2 * fm * dt
        var s = 0.0
        // Five partials, not eight: the wheel is a dark, buzzing thing, not a bright chorus.
        for (h in 1..5) { s += Math.sin(p1 * h) / h; s += 0.6 * Math.sin(p2 * h) / h }
        val raw = (s * 0.22).toFloat()
        // Weighted low and narrow. The organ's identity is its bright mixture, so the gurdy's has
        // to be the opposite: fundamental plus one boxy honk, and nothing much above it. Pushing
        // the formants harder than this measurably moves it TOWARDS the organ, not away.
        var v = lp.process(raw) * 1.15f + (nasal1.process(raw) + nasal2.process(raw)) * 0.5f
        v *= 1f + 0.20f * wob.toFloat()                  // crank pressure wobble, plainly audible
        out[i] = v
    }
    applyAR(out, sr, 0.12f, 0.20f)
    normalise(out, 0.85f)
    return out
}

/** Trompette buzz: the rhythmic 'chien' - raspy bridge chatter, square + bandpassed noise. */
private fun trompette(midi: Int, durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val f = midiHz(midi); val dt = 1.0 / sr; var phase = 0.0
    val rasp = Biquad.bandpass(sr, 3000f, 1.2f)
    for (i in 0 until n) {
        phase += 2.0 * Math.PI * f * dt
        val sq = if (Math.sin(phase) >= 0) 1f else -1f
        val noise = rasp.process(rng.nextFloat() * 2f - 1f)
        out[i] = sq * 0.25f + noise * 0.6f
    }
    applyAR(out, sr, 0.004f, 0.02f)
    normalise(out, 0.85f)
    return out
}

/**
 * Cathedral organ: a principal chorus (plenum) rather than a stack of harmonics on one oscillator.
 *
 * What makes a pipe organ sound like a pipe organ, and not like an organ-ish pad, is that each
 * footage is a SEPARATE RANK of pipes — its own pipe per note, its own tuning error, its own
 * speech. The mutations are the giveaway: the 2 2/3' quint and 1 3/5' tierce sound a fifth and a
 * third above, so the ear fuses the lot into one enormous hollow timbre it cannot decompose. That
 * is the sound of a building, and it is what separates this from GURDY below.
 *
 * Deliberately NO vibrato: an organ has none unless a tremulant is drawn, and putting vibrato on
 * it was a large part of why it read as a generic sustained pad.
 */
private fun organ(midi: Int, durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val f0 = midiHz(midi); val dt = 1.0 / sr
    // Footage ratios and their voicing, quietest at the top as a real chorus is scaled.
    //  0.5 = 16' sub   1 = 8' principal   2 = 4' octave   3 = 2 2/3' quint
    //  4 = 2' superoctave   5 = 1 3/5' tierce   6 = 1 1/3' larigot   8 = 1' mixture
    // A true plenum runs a Mixture of several ranks reaching far above the unison — at a low
    // pedal note an 8x mixture still only reaches ~1.2kHz, which left the whole instrument inside
    // GURDY's range and measurably indistinguishable from it. The 12th and 16th ranks are what
    // put a cathedral's glare above everything else in the consort.
    val ratios = doubleArrayOf(0.5, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 12.0, 16.0)
    // The 5th rank (the tierce) is drawn back deliberately: it is the most vowel-like of the
    // mutations and sits in the CHOIR's formant region, which is the one voice the organ can still
    // be confused with. Organ builders draw the tierce sparingly for much the same reason.
    val amps = doubleArrayOf(0.20, 0.70, 0.62, 0.48, 0.56, 0.16, 0.44, 0.44, 0.34, 0.26)
    // Each rank is its own pipework: a few cents out, and starting on its own phase.
    val detune = doubleArrayOf(-3.0, 0.0, 2.5, -4.5, 3.5, -6.0, 5.0, -7.5, 6.5, -9.0)
    val phases = DoubleArray(ratios.size) { rng.nextDouble() * 2.0 * Math.PI }
    // Ranks speak at slightly different moments — the chiff of a big chorus is not one event.
    val speak = doubleArrayOf(0.055, 0.030, 0.026, 0.020, 0.017, 0.014, 0.014, 0.011, 0.010, 0.009)
    val chiffBp = Biquad.bandpass(sr, (f0 * 4).toFloat().coerceAtMost(sr * 0.4f), 2.5f)
    val windLp = OnePoleLP(sr, 1200f)

    for (i in 0 until n) {
        val t = i * dt
        var s = 0.0
        for (r in ratios.indices) {
            val f = f0 * ratios[r] * Math.pow(2.0, detune[r] / 1200.0)
            if (f > sr * 0.45) continue
            phases[r] += 2.0 * Math.PI * f * dt
            // Each rank fades in over its own speech time rather than all arriving together.
            val onset = (t / speak[r]).coerceAtMost(1.0)
            s += amps[r] * onset * Math.sin(phases[r])
        }
        var v = (s * 0.26).toFloat()
        // Wind: the constant breath of the bellows, and the chiff as the pipes take speech.
        v += windLp.process(rng.nextFloat() * 2f - 1f) * 0.012f
        if (t < 0.04) v += chiffBp.process(rng.nextFloat() * 2f - 1f) * 0.16f * (1f - (t / 0.04f).toFloat())
        out[i] = v
    }
    // Slow release: pipes in a stone building do not stop dead. The reverb send does the rest.
    applyAR(out, sr, 0.045f, 0.18f)
    normalise(out, 0.85f)
    return out
}

/** Sustained vocal ensemble shaped around vowel formants that remain audible on phone speakers. */
private fun choir(midi: Int, durSec: Float, sr: Int, rng: Random, phraseIndex: Int): FloatArray {
    val n = (sr * durSec).toInt()
    val out = FloatArray(n)
    if (n == 0) return out
    val f0 = midiHz(midi)
    val ah = phraseIndex % 2 == 0
    val formants = if (ah) floatArrayOf(650f, 1080f, 2650f) else floatArrayOf(450f, 800f, 2830f)
    val formantAmps = if (ah) floatArrayOf(1.0f, 0.50f, 0.18f) else floatArrayOf(1.0f, 0.35f, 0.10f)
    val filters = Array(3) { Biquad.bandpass(sr, formants[it], 5.5f) }
    val tableSize = 2048
    val harmonicCount = minOf(32, (sr * 0.42 / f0).toInt().coerceAtLeast(5))
    val glottalTable = FloatArray(tableSize)
    for (i in glottalTable.indices) {
        val phase = 2.0 * Math.PI * i / tableSize
        var sample = 0.0
        for (harmonic in 1..harmonicCount) {
            sample += Math.sin(harmonic * phase) / Math.pow(harmonic.toDouble(), 1.15)
        }
        glottalTable[i] = sample.toFloat()
    }
    normalise(glottalTable, 0.9f)
    val detune = doubleArrayOf(0.0, -7.0, 6.0)
    val phases = DoubleArray(3) { rng.nextDouble() * tableSize }
    val dt = 1.0 / sr
    val attack = minOf(0.35f, durSec * 0.3f).coerceAtLeast(0.01f)
    val release = minOf(0.5f, durSec * 0.3f).coerceAtLeast(0.01f)
    for (i in 0 until n) {
        val t = i * dt
        val vibratoDepth = ((t - 0.5) / 0.7).coerceIn(0.0, 1.0) * 0.0035
        var source = 0.0
        for (voice in 0 until 3) {
            val vibrato = 1.0 + vibratoDepth *
                Math.sin(2.0 * Math.PI * (4.8 + voice * 0.17) * t + voice * 2.1)
            val frequency = f0 * Math.pow(2.0, detune[voice] / 1200.0) * vibrato
            phases[voice] += frequency * tableSize * dt
            phases[voice] -= Math.floor(phases[voice] / tableSize) * tableSize
            val index = phases[voice].toInt()
            val fraction = phases[voice] - index
            val next = glottalTable[(index + 1) % tableSize]
            source += glottalTable[index] * (1.0 - fraction) + next * fraction
        }
        val excitation = (source * 0.33 + (rng.nextFloat() * 2f - 1f) * 0.025).toFloat()
        var sample = 0f
        for (band in 0 until 3) {
            sample += filters[band].process(excitation) * formantAmps[band]
        }
        val time = t.toFloat()
        val envelope = (time / attack).coerceAtMost(1f) *
            ((durSec - time) / release).coerceIn(0f, 1f)
        out[i] = sample * envelope
    }
    normalise(out, 0.85f)
    return out
}

internal fun normalise(buf: FloatArray, target: Float) {
    var peak = 1e-6f; for (v in buf) peak = maxOf(peak, Math.abs(v))
    if (peak > target) for (i in buf.indices) buf[i] = buf[i] / peak * target
}

/**
 * Take the last few milliseconds of a note down to silence.
 *
 * Called once, from [renderNote], so it covers every voice — see the note there for why. Roughly
 * 4ms at 22kHz: long enough to remove the discontinuity, far too short to hear as a fade. It does
 * not shorten a drum, it just stops the buffer slamming the cone into the end of itself.
 *
 * Normalising cannot fix this and in fact worsens it: scaling the peak up to 0.9 scales a stranded
 * non-zero endpoint up with it.
 */
internal fun fadeOutTail(buf: FloatArray, samples: Int = 96) {
    val n = minOf(samples, buf.size)
    if (n <= 1) return
    val start = buf.size - n
    for (i in 0 until n) buf[start + i] *= 1f - i.toFloat() / (n - 1)
}
