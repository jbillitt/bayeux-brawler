import re

with open("app/src/main/java/com/example/game/SoundSynth.kt", "r") as f:
    content = f.read()

target = """    private fun harpNote(freq: Float, durationMs: Int, velocity: Float, sampleRate: Int): ShortArray {"""

replacement = """    private fun luteNote(freq: Float, durationMs: Int, velocity: Float, sampleRate: Int): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val fD = freq.toDouble()
        val velD = velocity.toDouble()
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Faster decay than harp, sharper pluck
            val envelope = if (t < 0.002) (t / 0.002) else kotlin.math.exp(-4.5 * (t - 0.002))
            val wave = (kotlin.math.sin(2 * kotlin.math.PI * fD * t)
                + 0.7 * kotlin.math.sin(2 * kotlin.math.PI * 2 * fD * t) * kotlin.math.exp(-2.0 * t)
                + 0.5 * kotlin.math.sin(2 * kotlin.math.PI * 3 * fD * t) * kotlin.math.exp(-4.0 * t)
                + 0.2 * kotlin.math.sin(2 * kotlin.math.PI * 4 * fD * t) * kotlin.math.exp(-6.0 * t))
            val noise = if (t < 0.01) (Math.random() * 2.0 - 1.0) * 0.25 * (1.0 - t / 0.01) else 0.0
            samples[i] = ((wave + noise) * envelope * velD * 6000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    private fun tambourineHit(durationMs: Int, sampleRate: Int, velocity: Float): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val velD = velocity.toDouble()
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = kotlin.math.exp(-12.0 * t)
            // Metallic noise (jingles)
            val noise = (Math.random() * 2.0 - 1.0)
            // High-pass filter approximation by subtracting smoothed noise
            val jingle = noise * kotlin.math.sin(2 * kotlin.math.PI * 8000.0 * t)
            samples[i] = (jingle * envelope * velD * 10000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    private fun harpNote(freq: Float, durationMs: Int, velocity: Float, sampleRate: Int): ShortArray {"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/SoundSynth.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
