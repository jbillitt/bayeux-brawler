package com.example.game

import kotlin.random.Random

internal fun renderPerc(voice: Voice, midi: Int, durSec: Float, velocity: Float, sr: Int, rng: Random): FloatArray = when (voice) {
    Voice.BELLS      -> bells(midi, durSec, sr, rng)
    // CALIBRATION: membrane() ends in normalise(0.9), so every hit peaks the same however these
    // are set — what they actually control is how much of that fixed peak sits in the 300-900Hz
    // knock/body bands a phone speaker can reproduce, versus the fundamental it cannot. Raising
    // slapAmp/bodyAmp is therefore the real "make the drums audible" lever; percBoost in
    // ProceduralMedievalComposer is the other. Tuned by ear on a handset.
    Voice.NAKERS     -> membrane(if (midi % 2 == 0) 150.0 else 200.0, durSec, sr, rng, t60 = 0.22f, drop = 0.03, noiseAmp = 1.2f, slapAmp = 2.2f, bodyAmp = 1.7f)
    Voice.TIMPANI    -> membrane(timpaniFundamental(midi), durSec, sr, rng, t60 = 1.1f, drop = 0.04, noiseAmp = 0.55f, slapAmp = 1.1f, bodyAmp = 1.6f)
    Voice.BODHRAN    -> membrane(72.0, durSec, sr, rng, t60 = 0.13f, drop = 0.05, noiseAmp = 0.85f, slapAmp = 1.2f, bodyAmp = 1.5f)
    Voice.TABOR      -> tabor(durSec, sr, rng)
    Voice.TAMBOURINE -> tambourine(durSec, sr, rng)
    Voice.EGG_SHAKER -> eggShaker(durSec, sr, rng)
    Voice.KICK       -> kick(durSec, sr, rng)
    else -> FloatArray((sr * durSec).toInt())
}

/**
 * Bass drum — the thing the metal kit was missing entirely. NAKERS is a small kettle drum tuned
 * at 150-200Hz; it can play a double-kick RHYTHM but it can never sound like a kick drum, which
 * is why the pattern read as a busy tom and not as two feet.
 *
 * A real kick's fundamental sits near 50Hz, which a phone speaker cannot move at all. So the
 * weight here is deliberately carried by the two parts that survive: the beater click at ~3kHz
 * and a 190Hz punch band. The pitch sweep is kept regardless — the drop from ~130Hz to ~50Hz is
 * what makes the ear hear "kick" rather than "thud" even when the bottom octave is missing.
 */
private fun kick(durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val dt = 1.0 / sr
    var phase = 0.0
    // A short, quiet beater click ONLY. The previous version ran a 30ms noise burst at 1.6x the
    // body's amplitude to satisfy a "must have energy above 300Hz" check — which made it a snare
    // with a hard attack, exactly as it sounded. Noise is the wrong way to make a kick carry.
    val click = Biquad.bandpass(sr, minOf(2200f, sr * 0.3f), 0.9f)

    for (i in 0 until n) {
        val t = i * dt
        // Pitch envelope: ~115Hz down to 47Hz. The drop is what the ear reads as "kick" rather
        // than "low tone", and it is the one part that must not be rushed.
        val f = 47.0 + 68.0 * Math.exp(-t / 0.042)
        phase += 2.0 * Math.PI * f * dt

        // Body with a real tail — 0.045s before, which is a click's length, not a drum's.
        val env = Math.exp(-t / 0.16) * (1.0 - Math.exp(-t / 0.0015))  // 1.5ms attack, no thump
        var v = (Math.sin(phase) * env).toFloat()

        // Saturation, which is how a kick survives a small speaker. tanh folds the sine into odd
        // harmonics at 2f/3f/4f — 95-350Hz here — and the ear reconstructs the fundamental it
        // cannot actually hear from them. This is the trick that makes an 808 translate on a
        // phone, and it costs none of the weight that piling on noise does.
        // Scaled to land near full scale: normalise() below only ever attenuates, so a sample
        // that peaks at 0.6 stays at 0.6 and the drum arrives at the mix quieter than every
        // other one before its gain is even applied. Prominence is set by the mix gain, once,
        // not by leaving the sample short.
        v = Math.tanh(v * 2.6).toFloat() * 0.92f

        // Beater: 4ms, well under the body. Present, not the event.
        if (t < 0.004) {
            val g = 1f - (t / 0.004f).toFloat()
            v += click.process(rng.nextFloat() * 2f - 1f) * 0.20f * g
        }
        out[i] = v
    }
    normalise(out, 0.95f)
    return out
}

/**
 * Egg shaker: a fistful of seed against plastic. Entirely high-frequency, which makes it the one
 * percussion voice that needs no phone-speaker compensation at all — it lives where a handset is
 * loudest, so it reads clearly even under a full mix where the drums are struggling.
 *
 * The gesture is two-part and that is what separates it from a hi-hat or the tambourine's jingles:
 * the seeds accelerate through the shell (a short noise swell) and then arrive together against
 * the far wall (a sharp burst). A single decaying noise burst sounds like neither.
 */
private fun eggShaker(durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    // Deliberately pitched ABOVE the tambourine, whose jingles peak at 3.4-6.8kHz: at 3.8/7.2kHz
    // the two measured as the same instrument. Seeds against plastic are a dry hiss with no
    // metallic ring, so the top of the spectrum is where it belongs and where it stays legible.
    // Relative to Nyquist, not absolute: the SFX path renders at 22050Hz, where a fixed 11.5kHz
    // bandpass is above Nyquist and the biquad returns NaN — silence, and a poisoned mix bus.
    val shell = Biquad.bandpass(sr, minOf(8500f, sr * 0.30f), 1.0f)
    val seeds = Biquad.bandpass(sr, minOf(11500f, sr * 0.40f), 0.8f)
    // A long swish, not a click. The tambourine is a sharp triple-burst of metal; if the shaker
    // is also a short sharp burst the two are the same event to the ear however their bands are
    // arranged — the envelope is doing as much of the distinguishing here as the filters.
    val travel = 0.035f          // how long the seeds take to cross the egg
    val dt = 1.0 / sr
    for (i in 0 until n) {
        val t = (i * dt).toFloat()
        val noise = rng.nextFloat() * 2f - 1f
        // Seeds gather speed, arrive, then hiss away against the shell.
        val env = if (t < travel) (t / travel) * (t / travel) * 0.7f
                  else Math.exp((-6.907755 * (t - travel) / 0.16f).toDouble()).toFloat()
        out[i] = (shell.process(noise) * 0.45f + seeds.process(noise) * 1.0f) * env
    }
    normalise(out, 0.9f)
    return out
}

/** Inharmonic bell partials with independent decays. */
private fun bells(midi: Int, durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val f0 = midiHz(midi)
    val ratios = doubleArrayOf(0.56, 1.0, 1.19, 1.5, 2.0, 2.74)
    val amps = floatArrayOf(0.35f, 1.0f, 0.55f, 0.4f, 0.5f, 0.22f)
    val t60s = floatArrayOf(4f, 3f, 2.2f, 1.6f, 1.2f, 0.8f)
    val dt = 1.0 / sr
    for (i in 0 until n) {
        val t = i * dt
        var s = 0.0
        for (p in ratios.indices) {
            val f = f0 * ratios[p]; if (f > sr * 0.45) continue
            s += amps[p] * Math.exp(-6.907755 * t / t60s[p]) * Math.sin(2.0 * Math.PI * f * t)
        }
        var v = (s * 0.28).toFloat()
        if (t < 0.008) v += (rng.nextFloat() * 2f - 1f) * 0.3f * (1f - (t / 0.008f).toFloat())
        out[i] = v
    }
    normalise(out, 0.9f)
    return out
}

private fun timpaniFundamental(midi: Int): Double {
    var drumMidi = midi
    while (drumMidi > 48) drumMidi -= 12
    while (drumMidi < 36) drumMidi += 12
    return midiHz(drumMidi)
}

/** Phone-first drum membrane with supporting lows and audible knock, body, and skin components. */
private fun membrane(
    f0: Double,
    durSec: Float,
    sr: Int,
    rng: Random,
    t60: Float,
    drop: Double,
    noiseAmp: Float,
    slapAmp: Float,
    bodyAmp: Float = 0.9f
): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val modes = doubleArrayOf(1.0, 1.5, 1.98, 2.44)
    val amps = floatArrayOf(0.5f, 0.22f, 0.14f, 0.07f)
    val dt = 1.0 / sr
    val phases = DoubleArray(modes.size)
    val skin = Biquad.bandpass(sr, 2400f, 1.2f)
    var knock = 0.0
    var body = 0.0
    val knockHz = (f0 * 3.7).coerceIn(220.0, 900.0)
    val bodyHz = (f0 * 5.5).coerceIn(300.0, 520.0)
    val bodyT60 = (t60 * 0.55f).coerceIn(0.08f, 0.5f)
    for (i in 0 until n) {
        val t = i * dt
        val bend = 1.0 + drop * Math.exp(-t / 0.06)
        var s = 0.0
        for (m in modes.indices) {
            phases[m] += 2.0 * Math.PI * f0 * modes[m] * bend * dt
            s += amps[m] * Math.exp(-6.907755 * t / (t60 * (1.0 - 0.15 * m))) * Math.sin(phases[m])
        }
        knock += 2.0 * Math.PI * knockHz * bend * dt
        s += slapAmp * Math.exp(-6.907755 * t / 0.05) * Math.sin(knock)
        body += 2.0 * Math.PI * bodyHz * bend * dt
        s += bodyAmp * Math.exp(-6.907755 * t / bodyT60) * Math.sin(body)
        var v = (s * 0.5).toFloat()
        if (t < 0.025) v += skin.process(rng.nextFloat() * 2f - 1f) * noiseAmp * (1f - (t / 0.025f).toFloat())
        out[i] = v
    }
    normalise(out, 0.9f)
    return out
}

private fun tabor(durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val dt = 1.0 / sr; var phase = 0.0
    // Two snare bands — a single 2 kHz band read as a filtered hiss, not a rattle.
    val snare = Biquad.bandpass(sr, 2000f, 0.8f)
    val rattle = Biquad.bandpass(sr, 3400f, 1.5f)
    for (i in 0 until n) {
        val t = i * dt
        phase += 2.0 * Math.PI * 175.0 * (1.0 + 0.04 * Math.exp(-t / 0.05)) * dt
        var v = (Math.exp(-6.907755 * t / 0.09) * Math.sin(phase)).toFloat() * 0.12f
        val exc = rng.nextFloat() * 2f - 1f
        v += (snare.process(exc) * 1.0f + rattle.process(exc) * 0.7f) * Math.exp(-6.907755 * t / 0.07).toFloat()
        out[i] = v
    }
    normalise(out, 0.85f)
    return out
}

private fun tambourine(durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val bursts = floatArrayOf(0f, 0.012f, 0.028f)
    val frame = Biquad.bandpass(sr, 900f, 1.4f)
    val jingle0 = Biquad.bandpass(sr, 3400f, 4f)
    val jingle1 = Biquad.bandpass(sr, 5200f, 6f)
    val jingle2 = Biquad.bandpass(sr, 6800f, 6f)
    val dt = 1.0 / sr
    for (i in 0 until n) {
        val t = (i * dt).toFloat()
        var exc = 0f
        for (b in bursts) if (t >= b && t < b + 0.05f) exc += (rng.nextFloat() * 2f - 1f) * Math.exp(-(t - b) / 0.02).toFloat()
        // The wooden frame carries more weight than it did: the jingles alone put the tambourine
        // in the same airy band as the egg shaker, and the two measured as one instrument.
        out[i] = frame.process(exc) * 0.60f + jingle0.process(exc) * 0.70f +
            jingle1.process(exc) * 0.55f + jingle2.process(exc) * 0.25f
    }
    normalise(out, 0.8f)
    return out
}
