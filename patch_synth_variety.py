import re

with open("app/src/main/java/com/example/game/SoundSynth.kt", "r") as f:
    content = f.read()

target_clang = """            SoundType.CLANG -> {
                val duration = 0.25f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = (1.0f - t / duration) * (1.0f - t / duration)
                    val sine = sin(2 * PI * 880 * t) + 0.6 * sin(2 * PI * 1320 * t) + 0.4 * sin(2 * PI * 1760 * t)
                    val noise = (Math.random() * 2 - 1) * 0.15
                    val value = ((sine + noise) / 1.75 * envelope * 28000).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }"""

replacement_clang = """            SoundType.CLANG -> {
                val duration = 0.25f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                val baseFreq = 700 + Math.random() * 400
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = (1.0f - t / duration) * (1.0f - t / duration)
                    val sine = sin(2 * PI * baseFreq * t) + 0.6 * sin(2 * PI * baseFreq * 1.5 * t) + 0.4 * sin(2 * PI * baseFreq * 2.0 * t)
                    val noise = (Math.random() * 2 - 1) * 0.15
                    val value = ((sine + noise) / 1.75 * envelope * 28000).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }"""

if target_clang in content:
    content = content.replace(target_clang, replacement_clang)

target_crunch = """            SoundType.CRUNCH -> {
                val duration = 0.25f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = kotlin.math.exp(-15.0 * t) // fast decay
                    val freq = 80f * kotlin.math.exp(-5.0 * t) // pitch drop
                    val sine = kotlin.math.sin(2 * kotlin.math.PI * freq * t)
                    
                    // heavily distorted noise for bone crushing
                    val noise1 = (Math.random() * 2 - 1)
                    val noise2 = (Math.random() * 2 - 1) * 0.5 * kotlin.math.sin(2 * kotlin.math.PI * 300 * t)
                    
                    val value = ((sine * 0.3 + noise1 * 0.5 + noise2) * envelope * 30000).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }"""
            
replacement_crunch = """            SoundType.CRUNCH -> {
                val duration = 0.25f + Math.random() * 0.15
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                val freqBase = 60f + Math.random() * 40f
                val crushResonance = 200 + Math.random() * 200
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = kotlin.math.exp(-15.0 * t) // fast decay
                    val freq = freqBase * kotlin.math.exp(-5.0 * t) // pitch drop
                    val sine = kotlin.math.sin(2 * kotlin.math.PI * freq * t)
                    
                    // heavily distorted noise for bone crushing
                    val noise1 = (Math.random() * 2 - 1)
                    val noise2 = (Math.random() * 2 - 1) * 0.5 * kotlin.math.sin(2 * kotlin.math.PI * crushResonance * t)
                    
                    val value = ((sine * 0.3 + noise1 * 0.5 + noise2) * envelope * 30000).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }"""

if target_crunch in content:
    content = content.replace(target_crunch, replacement_crunch)

with open("app/src/main/java/com/example/game/SoundSynth.kt", "w") as f:
    f.write(content)
print("Replaced Synth successfully")
