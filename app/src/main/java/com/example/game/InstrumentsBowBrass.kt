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
    Voice.VIELLE  -> bowed(midi, durSec, sr, rng, bodyHz = floatArrayOf(340f, 850f, 1750f), vibHz = 5.0f, detuneCents = 0f, attack = 0.07f, harmonicSlope = 1.0, bowNoise = 0.07f)
    Voice.VIOLA   -> bowed(midi, durSec, sr, rng, bodyHz = floatArrayOf(220f, 500f, 1000f), vibHz = 4.3f, detuneCents = 0f, attack = 0.13f, harmonicSlope = 1.3, bowNoise = 0.035f)
    Voice.FIDDLE2 -> bowed(midi, durSec, sr, rng, bodyHz = floatArrayOf(420f, 1050f, 2200f), vibHz = 5.8f, detuneCents = 4f, attack = 0.04f, harmonicSlope = 0.82, bowNoise = 0.11f)
    Voice.SHAWM   -> shawm(midi, durSec, sr, rng)
    Voice.SACKBUT -> brass(midi, durSec, sr, rng, brightness = 1.25f, attack = 0.045f, cutoffHz = 3400f, lipNoise = 0.025f)
    Voice.HORN    -> brass(midi, durSec, sr, rng, brightness = 0.82f, attack = 0.11f, cutoffHz = 1800f, lipNoise = 0.01f)
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
        for (h in 1..6) {
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

/** Hurdy-gurdy wheel: a doubled unison; orchestration supplies the root and modal fifth.
 *  What sells "gurdy" over "string pad": the crank's once-around flutter (pitch + pressure wobble
 *  at ~1.7 Hz) and the nasal formant resonances of the boxy body. */
private fun wheelDrone(midi: Int, durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val center = midiHz(midi)
    val f1 = center * Math.pow(2.0, -3.0 / 1200.0)
    val f2 = center * Math.pow(2.0, 3.0 / 1200.0)
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
