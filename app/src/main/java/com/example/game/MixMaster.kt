package com.example.game

import kotlin.random.Random

class MixBus(val sampleRate: Int, val totalSamples: Int) {
    private val left = FloatArray(totalSamples)
    private val right = FloatArray(totalSamples)
    private val send = FloatArray(totalSamples)

    fun add(mono: FloatArray, offsetSamples: Int, gain: Float, pan: Float, reverbSend: Float) {
        val lg = Math.cos((pan + 1.0) * Math.PI / 4.0).toFloat() * gain
        val rg = Math.sin((pan + 1.0) * Math.PI / 4.0).toFloat() * gain
        for (i in mono.indices) {
            val idx = offsetSamples + i
            if (idx < 0 || idx >= totalSamples) continue
            val v = mono[i]
            left[idx] += v * lg
            right[idx] += v * rg
            send[idx] += v * gain * reverbSend
        }
    }

    private class Comb(val buf: FloatArray, val g: Float) { var ptr = 0
        fun process(x: Float): Float { val y = buf[ptr]; buf[ptr] = x + y * g; ptr = (ptr + 1) % buf.size; return y } }
    private class Allpass(val buf: FloatArray) { var ptr = 0
        fun process(x: Float): Float { val b = buf[ptr]; val y = -x + b; buf[ptr] = x + b * 0.5f; ptr = (ptr + 1) % buf.size; return y } }

    fun masterToStereoPcm(loopAware: Boolean, rng: Random): ShortArray {
        // Schroeder reverb over the send bus; loop-aware = one warm-up pass keeping state, then the write pass
        val combDelaysMs = floatArrayOf(29.7f, 37.1f, 41.1f, 43.7f)
        val rt60 = 1.2f
        val combs = combDelaysMs.map { d ->
            val len = (sampleRate * d / 1000f).toInt()
            Comb(FloatArray(len), Math.pow(10.0, (-3.0 * d / 1000.0 / rt60)).toFloat())
        }
        val apL = Allpass(FloatArray((sampleRate * 0.0050f).toInt()))
        val apR = Allpass(FloatArray((sampleRate * 0.0017f).toInt()))
        val wetL = FloatArray(totalSamples); val wetR = FloatArray(totalSamples)
        val passes = if (loopAware) 2 else 1
        for (pass in 0 until passes) {
            val write = pass == passes - 1
            for (i in 0 until totalSamples) {
                var s = 0f
                for (c in combs) s += c.process(send[i])
                s *= 0.25f
                val l = apL.process(s); val r = apR.process(l)
                if (write) { wetL[i] = l; wetR[i] = r }
            }
        }

        // Master chain per channel: DC block -> HPF 70 -> soft limiter -> dither -> 16-bit
        val out = ShortArray(totalSamples * 2)
        val ceiling = 23196f   // -3 dBFS
        for (ch in 0 until 2) {
            val dry = if (ch == 0) left else right
            val wet = if (ch == 0) wetL else wetR
            val hpf = Biquad.highpass(sampleRate, 70f, 0.707f)
            var dcX1 = 0f; var dcY1 = 0f
            for (i in 0 until totalSamples) {
                var v = dry[i] + wet[i]
                val dc = v - dcX1 + 0.995f * dcY1; dcX1 = v; dcY1 = dc; v = dc
                v = hpf.process(v)
                val t = 0.7f
                val av = Math.abs(v)
                if (av > t) v = Math.signum(v) * (t + (1f - t) * Math.tanh(((av - t) / (1f - t)).toDouble()).toFloat())
                val dith = (rng.nextFloat() - rng.nextFloat())   // TPDF, +/-1 LSB
                out[i * 2 + ch] = ((v * ceiling) + dith).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return out
    }
}
