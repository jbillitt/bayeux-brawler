package com.example.game

import kotlin.random.Random

enum class Voice { HARP, LUTE, PSALTERY, VIELLE, VIOLA, FIDDLE2, RECORDER, PANPIPES, SHAWM, SACKBUT, HORN, GURDY, ORGAN, CHOIR, BELLS, NAKERS, TIMPANI, BODHRAN, TABOR, TAMBOURINE }

fun renderNote(
    voice: Voice,
    midi: Int,
    durSec: Float,
    velocity: Float,
    sr: Int,
    rng: Random,
    phraseIndex: Int = 0
): FloatArray {
    val buf = when (voice) {
        Voice.HARP     -> karplusStrong(midiHz(midi), durSec, sr, t60 = 3.2f, brightness = 8f,  pickPos = 0.30f, rng = rng)
        Voice.LUTE     -> karplusStrong(midiHz(midi), durSec, sr, t60 = 1.6f, brightness = 5f,  pickPos = 0.22f, rng = rng)
        Voice.PSALTERY -> psaltery(midi, durSec, sr, rng)
        Voice.RECORDER -> windVoice(midi, durSec, sr, rng, breath = 0.05f, chiffSec = 0.04f, chiffAmp = 0.12f,
                                     harmonics = floatArrayOf(1f, 0.25f, 0.12f, 0.05f), vibCents = 6f, vibHz = 5.0f, scoop = 0f,
                                     breathQ = 2f, attack = 0.02f)
        // Panpipes read as panpipes through the breathy "pah": pitched breath noise (high-Q band
        // at the fundamental) and a hard chiff. Without them it's just a quiet sine.
        Voice.PANPIPES -> windVoice(midi, durSec, sr, rng, breath = 0.20f, chiffSec = 0.04f, chiffAmp = 0.35f,
                                     harmonics = floatArrayOf(1f, 0.15f, 0.05f), vibCents = 0f, vibHz = 0f, scoop = 0.04f,
                                     breathQ = 6f, attack = 0.05f)
        Voice.VIELLE, Voice.VIOLA, Voice.FIDDLE2, Voice.SHAWM, Voice.SACKBUT, Voice.HORN, Voice.GURDY, Voice.ORGAN, Voice.CHOIR ->
            renderBowBrass(voice, midi, durSec, velocity, sr, rng, phraseIndex)
        Voice.BELLS, Voice.NAKERS, Voice.TIMPANI, Voice.BODHRAN, Voice.TABOR, Voice.TAMBOURINE ->
            renderPerc(voice, midi, durSec, velocity, sr, rng)
    }
    if (velocity != 1f) for (i in buf.indices) buf[i] *= velocity
    return buf
}

private fun psaltery(midi: Int, durSec: Float, sr: Int, rng: Random): FloatArray {
    val f = midiHz(midi)
    val a = karplusStrong(f * Math.pow(2.0, -3.0 / 1200.0), durSec, sr, 4.5f, 14f, 0.15f, rng)
    val b = karplusStrong(f * Math.pow(2.0,  3.0 / 1200.0), durSec, sr, 4.5f, 14f, 0.15f, rng)
    val out = FloatArray(a.size)
    for (i in out.indices) out[i] = (a[i] + b[i]) * 0.55f
    return out
}

/** Phase-accumulator additive wind. Vibrato and scoop are integrated into phase - they can never run away. */
private fun windVoice(
    midi: Int, durSec: Float, sr: Int, rng: Random,
    breath: Float, chiffSec: Float, chiffAmp: Float,
    harmonics: FloatArray, vibCents: Float, vibHz: Float, scoop: Float,
    breathQ: Float, attack: Float
): FloatArray {
    val n = (sr * durSec).toInt()
    val out = FloatArray(n)
    if (n == 0) return out
    val f0 = midiHz(midi)
    val dt = 1.0 / sr
    var phase = 0.0
    val breathBp = Biquad.bandpass(sr, f0.toFloat().coerceAtMost(sr * 0.4f), breathQ)
    val vibOnset = 0.3
    for (i in 0 until n) {
        val t = i * dt
        val vibDepth = if (vibHz > 0f) vibCents * ((t - vibOnset) / 0.4).coerceIn(0.0, 1.0) else 0.0
        val cents = vibDepth * Math.sin(2.0 * Math.PI * vibHz * t)
        val scoopMul = 1.0 - scoop * (1.0 - (t / 0.03).coerceAtMost(1.0))
        val f = f0 * Math.pow(2.0, cents / 1200.0) * scoopMul
        phase += 2.0 * Math.PI * f * dt
        var s = 0.0
        for ((h, amp) in harmonics.withIndex()) {
            val hf = f * (h + 1)
            if (hf < sr * 0.45) s += amp * Math.sin(phase * (h + 1))
        }
        var v = s.toFloat() * 0.5f
        v += breathBp.process(rng.nextFloat() * 2f - 1f) * breath
        if (t < chiffSec) v += (rng.nextFloat() * 2f - 1f) * chiffAmp * (1f - (t / chiffSec).toFloat())
        out[i] = v
    }
    applyAR(out, sr, attack, 0.06f)
    var peak = 1e-6f; for (v in out) peak = maxOf(peak, Math.abs(v))
    if (peak > 0.95f) for (i in out.indices) out[i] = out[i] / peak * 0.95f
    return out
}
