package com.example.game

import kotlin.random.Random

fun midiHz(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

/** RBJ biquad. */
class Biquad private constructor(
    private val b0: Float, private val b1: Float, private val b2: Float,
    private val a1: Float, private val a2: Float
) {
    private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f
    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
    fun processBuf(buf: FloatArray) { for (i in buf.indices) buf[i] = process(buf[i]) }

    companion object {
        private fun norm(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad((b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(), (a1 / a0).toFloat(), (a2 / a0).toFloat())

        fun lowpass(sr: Int, fc: Float, q: Float): Biquad {
            val w = 2.0 * Math.PI * fc / sr; val c = Math.cos(w); val s = Math.sin(w); val alpha = s / (2 * q)
            return norm((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }
        fun highpass(sr: Int, fc: Float, q: Float): Biquad {
            val w = 2.0 * Math.PI * fc / sr; val c = Math.cos(w); val s = Math.sin(w); val alpha = s / (2 * q)
            return norm((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }
        fun bandpass(sr: Int, fc: Float, q: Float): Biquad {
            val w = 2.0 * Math.PI * fc / sr; val c = Math.cos(w); val s = Math.sin(w); val alpha = s / (2 * q)
            return norm(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
        }
        fun peakEq(sr: Int, fc: Float, q: Float, gainDb: Float): Biquad {
            val a = Math.pow(10.0, gainDb / 40.0)
            val w = 2.0 * Math.PI * fc / sr; val c = Math.cos(w); val s = Math.sin(w); val alpha = s / (2 * q)
            return norm(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
        }
    }
}

class OnePoleLP(sr: Int, fc: Float) {
    private val alpha: Float
    private var y = 0f
    init {
        val rc = 1.0 / (2.0 * Math.PI * fc); val dt = 1.0 / sr
        alpha = (dt / (rc + dt)).toFloat()
    }
    fun process(x: Float): Float { y += alpha * (x - y); return y }
}

fun applyAR(buf: FloatArray, sr: Int, attackSec: Float, releaseSec: Float) {
    val a = (sr * attackSec).toInt().coerceAtLeast(1).coerceAtMost(buf.size)
    val r = (sr * releaseSec).toInt().coerceAtLeast(1).coerceAtMost(buf.size)
    for (i in 0 until a) buf[i] *= i.toFloat() / a
    for (i in 0 until r) buf[buf.size - 1 - i] *= i.toFloat() / r
}

/**
 * Tuned Karplus-Strong: rounded integer delay + first-order allpass fractional tuning.
 * t60 = seconds to decay 60 dB. brightness = excitation lowpass cutoff as multiple of freq.
 * pickPos in (0,1): pick-position comb on the excitation.
 */
fun karplusStrong(freqHz: Double, durSec: Float, sr: Int, t60: Float, brightness: Float, pickPos: Float, rng: Random): FloatArray {
    val numSamples = (sr * durSec).toInt()
    val out = FloatArray(numSamples)
    if (freqHz <= 20.0 || numSamples == 0) return out

    // Loop delay budget, tuned empirically (Task 4 report): the averaging filter and the
    // y=a*x+x[n-1]-a*y[n-1] allpass together contribute ~n-0.5+frac samples, so the +0.5
    // bias below centres the fractional part; measured worst-case error +0.89 cents.
    val delayExact = sr / freqHz + 0.5
    var n = Math.floor(delayExact - 0.2).toInt()
    if (n < 2) n = 2
    val frac = (delayExact - n).coerceIn(0.05, 1.2)
    val apA = ((1.0 - frac) / (1.0 + frac)).toFloat()

    // Excitation: lowpassed noise + pick-position comb + tiny ramp
    val ex = FloatArray(n)
    val lp = OnePoleLP(sr, (freqHz * brightness).toFloat().coerceAtMost(sr * 0.45f))
    for (i in 0 until n) ex[i] = lp.process((rng.nextFloat() * 2f - 1f))
    val pick = (n * pickPos).toInt().coerceIn(1, n - 1)
    for (i in n - 1 downTo pick) ex[i] -= ex[i - pick] * 0.9f
    var exMax = 1e-6f; for (v in ex) exMax = maxOf(exMax, Math.abs(v))
    for (i in ex.indices) ex[i] = ex[i] / exMax * 0.9f

    val delay = ex.copyOf()
    var ptr = 0
    val g = Math.pow(10.0, -3.0 / (t60 * sr)).toFloat()   // per-sample decay for t60
    var apX1 = 0f; var apY1 = 0f
    for (i in 0 until numSamples) {
        val cur = delay[ptr]
        val nxt = delay[(ptr + 1) % n]
        var v = g * 0.5f * (cur + nxt)                    // damping + half-sample delay
        val y = apA * v + apX1 - apA * apY1               // fractional-delay allpass
        apX1 = v; apY1 = y; v = y
        delay[ptr] = v
        out[i] = v
        ptr = (ptr + 1) % n
    }
    // 3 ms attack ramp kills the raw-noise onset click
    val ramp = (sr * 0.003f).toInt().coerceAtMost(numSamples)
    for (i in 0 until ramp) out[i] *= i.toFloat() / ramp
    var peak = 1e-6f; for (v in out) peak = maxOf(peak, Math.abs(v))
    if (peak > 1f) for (i in out.indices) out[i] /= peak
    return out
}
