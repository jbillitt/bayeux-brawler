package com.example.game

import kotlin.random.Random

internal fun renderPerc(voice: Voice, midi: Int, durSec: Float, velocity: Float, sr: Int, rng: Random): FloatArray = when (voice) {
    Voice.BELLS      -> bells(midi, durSec, sr, rng)
    Voice.NAKERS     -> membrane(if (midi % 2 == 0) 150.0 else 200.0, durSec, sr, rng, t60 = 0.22f, drop = 0.03, noiseAmp = 0.35f)
    Voice.TIMPANI    -> membrane(midiHz(midi).coerceIn(80.0, 120.0), durSec, sr, rng, t60 = 1.1f, drop = 0.04, noiseAmp = 0.25f)
    Voice.BODHRAN    -> membrane(72.0, durSec, sr, rng, t60 = 0.13f, drop = 0.05, noiseAmp = 0.30f)
    Voice.TABOR      -> tabor(durSec, sr, rng)
    Voice.TAMBOURINE -> tambourine(durSec, sr, rng)
    else -> FloatArray((sr * durSec).toInt())
}

/** Inharmonic bell partials with independent decays. */
private fun bells(midi: Int, durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val f0 = midiHz(midi)
    val ratios = doubleArrayOf(0.56, 0.92, 1.19, 1.71, 2.0, 2.74)
    val amps = floatArrayOf(0.6f, 1.0f, 0.7f, 0.5f, 0.6f, 0.3f)
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

/** Drum membrane: modal damped sines + skin noise; pitch drop is phase-integrated (no v1 chirp bug). */
private fun membrane(f0: Double, durSec: Float, sr: Int, rng: Random, t60: Float, drop: Double, noiseAmp: Float): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val modes = doubleArrayOf(1.0, 1.5, 1.98, 2.44)
    val amps = floatArrayOf(1.0f, 0.4f, 0.25f, 0.12f)
    val dt = 1.0 / sr
    val phases = DoubleArray(modes.size)
    val skin = Biquad.bandpass(sr, 1200f, 1.5f)
    for (i in 0 until n) {
        val t = i * dt
        val bend = 1.0 + drop * Math.exp(-t / 0.06)      // starts sharp, settles - integrated below
        var s = 0.0
        for (m in modes.indices) {
            phases[m] += 2.0 * Math.PI * f0 * modes[m] * bend * dt
            s += amps[m] * Math.exp(-6.907755 * t / (t60 * (1.0 - 0.15 * m))) * Math.sin(phases[m])
        }
        var v = (s * 0.5).toFloat()
        if (t < 0.015) v += skin.process(rng.nextFloat() * 2f - 1f) * noiseAmp * (1f - (t / 0.015f).toFloat())
        out[i] = v
    }
    normalise(out, 0.9f)
    return out
}

private fun tabor(durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val dt = 1.0 / sr; var phase = 0.0
    val snare = Biquad.bandpass(sr, 2000f, 0.8f)
    for (i in 0 until n) {
        val t = i * dt
        phase += 2.0 * Math.PI * 140.0 * (1.0 + 0.04 * Math.exp(-t / 0.05)) * dt
        var v = (Math.exp(-6.907755 * t / 0.09) * Math.sin(phase)).toFloat() * 0.5f
        v += snare.process(rng.nextFloat() * 2f - 1f) * 0.4f * Math.exp(-6.907755 * t / 0.06).toFloat()
        out[i] = v
    }
    normalise(out, 0.85f)
    return out
}

private fun tambourine(durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val bursts = floatArrayOf(0f, 0.012f, 0.028f)
    val jingle1 = Biquad.bandpass(sr, 5200f, 6f)
    val jingle2 = Biquad.bandpass(sr, 6800f, 6f)
    val dt = 1.0 / sr
    for (i in 0 until n) {
        val t = (i * dt).toFloat()
        var exc = 0f
        for (b in bursts) if (t >= b && t < b + 0.05f) exc += (rng.nextFloat() * 2f - 1f) * Math.exp(-(t - b) / 0.02).toFloat()
        out[i] = (jingle1.process(exc) + jingle2.process(exc)) * 0.8f
    }
    normalise(out, 0.8f)
    return out
}
