import re

with open("app/src/main/java/com/example/game/SoundSynth.kt", "r") as f:
    content = f.read()

target = """        // ---- BONUS: Trumpeter ancillary ----
        if (hasTrumpeter) {
            writeTrumpeterLayer(master, scale, rng, stepMs, totalSteps, sampleRate)
        }

        return master"""

replacement = """        // ---- BONUS: Trumpeter ancillary ----
        if (hasTrumpeter) {
            writeTrumpeterLayer(master, scale, rng, stepMs, totalSteps, sampleRate)
        }

        // --- MASTERING: CAVERNOUS CASTLE REVERB (Wrap-around Delay) ---
        // We use a dotted-8th note delay (3 steps) to create rhythmic echoes
        // that bleed beautifully across the loop seam.
        val delaySamples = sampleRate * (stepMs * 3) / 1000
        val feedback = 0.4f
        val wetMix = 0.5f // 50% wet
        
        // We need a temporary buffer to avoid feedback explosion during the loop pass
        val wetBuffer = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val srcIndex = (i - delaySamples + totalSamples) % totalSamples
            // Simple low-pass filter on the echo to make it darker (castle walls)
            val prevSrc = (srcIndex - 1 + totalSamples) % totalSamples
            val echoRaw = (master[srcIndex] * 0.6f + master[prevSrc] * 0.4f)
            val echo = (echoRaw * feedback) + (wetBuffer[srcIndex] * feedback * 0.5f)
            wetBuffer[i] = echo.toInt().coerceIn(-32768, 32767).toShort()
        }
        
        // Mix dry + wet and apply a subtle soft-clipper/limiter
        for (i in 0 until totalSamples) {
            val dry = master[i].toFloat()
            val wet = wetBuffer[i].toFloat() * wetMix
            var out = dry + wet
            // Soft clipping
            out = 32767f * kotlin.math.tanh(out / 32767f)
            master[i] = out.toInt().coerceIn(-32768, 32767).toShort()
        }

        return master"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/game/SoundSynth.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
