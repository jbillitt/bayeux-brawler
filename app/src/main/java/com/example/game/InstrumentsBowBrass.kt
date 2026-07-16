package com.example.game

import kotlin.random.Random

internal fun renderBowBrass(voice: Voice, midi: Int, durSec: Float, velocity: Float, sr: Int, rng: Random): FloatArray = when (voice) {
    Voice.VIELLE  -> bowed(midi, durSec, sr, rng, bodyHz = floatArrayOf(300f, 700f, 1500f), vibHz = 5.0f, detuneCents = 0f, attack = 0.08f)
    Voice.VIOLA   -> bowed(midi, durSec, sr, rng, bodyHz = floatArrayOf(250f, 600f, 1200f), vibHz = 4.6f, detuneCents = 0f, attack = 0.10f)
    Voice.FIDDLE2 -> bowed(midi, durSec, sr, rng, bodyHz = floatArrayOf(320f, 740f, 1550f), vibHz = 5.6f, detuneCents = 4f, attack = 0.08f)
    Voice.SHAWM   -> shawm(midi, durSec, sr, rng)
    Voice.SACKBUT -> brass(midi, durSec, sr, rng, brightness = 1.2f, attack = 0.07f)
    // Horn plays sustained pads and fanfares — the trompette buzz (60% noise) belongs to the
    // gurdy's chien, not to a horn. Mellow brass, slower speak.
    Voice.HORN    -> brass(midi, durSec, sr, rng, brightness = 0.95f, attack = 0.05f)
    Voice.GURDY   -> if (durSec < 0.15f) trompette(midi, durSec, sr, rng) else wheelDrone(midi, durSec, sr, rng)
    Voice.ORGAN   -> organ(midi, durSec, sr, rng)
    else -> FloatArray((sr * durSec).toInt())
}

/** Band-limited saw via additive harmonics, phase-accumulated vibrato, bow noise, body resonances. */
private fun bowed(midi: Int, durSec: Float, sr: Int, rng: Random, bodyHz: FloatArray, vibHz: Float, detuneCents: Float, attack: Float): FloatArray {
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
        for (h in 1..hCount) s += Math.sin(phase * h) / h
        var v = (s * 0.45).toFloat()
        v += bowLp.process(rng.nextFloat() * 2f - 1f) * 0.06f
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

private fun brass(midi: Int, durSec: Float, sr: Int, rng: Random, brightness: Float, attack: Float): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val f0 = midiHz(midi); val dt = 1.0 / sr; var phase = 0.0
    for (i in 0 until n) {
        val t = i * dt
        phase += 2.0 * Math.PI * f0 * dt
        var s = 0.0
        for (h in 1..6) {
            val hf = f0 * h; if (hf > sr * 0.45) break
            val swell = (t / (0.04 * h)).coerceAtMost(1.0)   // upper harmonics bloom in - brass swell
            s += swell * Math.sin(phase * h) / Math.pow(h.toDouble(), (1.2 / brightness))
        }
        out[i] = (s * 0.4).toFloat()
    }
    applyAR(out, sr, attack, 0.12f)
    normalise(out, 0.9f)
    return out
}

/** Hurdy-gurdy wheel: root + fifth, slightly detuned saws through a lowpass.
 *  What sells "gurdy" over "string pad": the crank's once-around flutter (pitch + pressure wobble
 *  at ~1.7 Hz) and the nasal formant resonances of the boxy body. */
private fun wheelDrone(midi: Int, durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val f1 = midiHz(midi); val f2 = f1 * 1.5 * Math.pow(2.0, 3.0 / 1200.0)
    val dt = 1.0 / sr; var p1 = 0.0; var p2 = 0.0
    val lp = OnePoleLP(sr, 2200f)
    val nasal1 = Biquad.bandpass(sr, 1100f, 4f)
    val nasal2 = Biquad.bandpass(sr, 1700f, 5f)
    for (i in 0 until n) {
        val t = i * dt
        val wob = Math.sin(2.0 * Math.PI * 1.7 * t)
        val fm = 1.0 + 0.004 * wob                       // ±7 cents, integrated so it can't run away
        p1 += 2.0 * Math.PI * f1 * fm * dt; p2 += 2.0 * Math.PI * f2 * fm * dt
        var s = 0.0
        for (h in 1..8) { s += Math.sin(p1 * h) / h; s += 0.6 * Math.sin(p2 * h) / h }
        val raw = (s * 0.22).toFloat()
        var v = lp.process(raw) + (nasal1.process(raw) + nasal2.process(raw)) * 0.5f
        v *= 1f + 0.12f * wob.toFloat()                  // crank pressure wobble
        out[i] = v
    }
    applyAR(out, sr, 0.15f, 0.25f)
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

private fun organ(midi: Int, durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val f0 = midiHz(midi); val dt = 1.0 / sr; var phase = 0.0
    val chiffBp = Biquad.bandpass(sr, (f0 * 2).toFloat().coerceAtMost(sr * 0.4f), 3f)
    for (i in 0 until n) {
        val t = i * dt
        val glide = 1.0 - 0.015 * (1.0 - (t / 0.025).coerceAtMost(1.0))   // starts 1.5% flat, integrated
        phase += 2.0 * Math.PI * f0 * glide * dt
        var v = (Math.sin(phase) + 0.5 * Math.sin(phase * 2) + 0.2 * Math.sin(phase * 4)).toFloat() * 0.35f
        if (t < 0.03) v += chiffBp.process(rng.nextFloat() * 2f - 1f) * 0.2f * (1f - (t / 0.03f).toFloat())
        out[i] = v
    }
    applyAR(out, sr, 0.015f, 0.06f)
    normalise(out, 0.85f)
    return out
}

internal fun normalise(buf: FloatArray, target: Float) {
    var peak = 1e-6f; for (v in buf) peak = maxOf(peak, Math.abs(v))
    if (peak > target) for (i in buf.indices) buf[i] = buf[i] / peak * target
}
