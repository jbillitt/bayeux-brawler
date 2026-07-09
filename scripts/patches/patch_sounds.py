import re

with open("app/src/main/java/com/example/game/SoundSynth.kt", "r") as f:
    content = f.read()

target = """            SoundType.CLANG -> {
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
            }
            
            SoundType.THWACK -> {"""

replacement = """            SoundType.CLANG -> {
                // More variation: heavy vs light clink
                val duration = 0.2f + Math.random() * 0.15f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                val baseFreq = 600 + Math.random() * 700
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = (1.0f - t / duration) * (1.0f - t / duration)
                    val sine = sin(2 * PI * baseFreq * t) + 0.6 * sin(2 * PI * baseFreq * 1.5 * t) + 0.4 * sin(2 * PI * baseFreq * 2.0 * t)
                    val noise = (Math.random() * 2 - 1) * 0.2
                    val value = ((sine + noise) / 1.8 * envelope * 29000).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }
            
            SoundType.THWACK -> {"""

if target in content:
    content = content.replace(target, replacement)
    
target2 = """            SoundType.CRUNCH -> {
                val duration = 0.25f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = kotlin.math.exp(-15.0 * t) // fast decay
                    val freq = 80f * kotlin.math.exp(-5.0 * t) // pitch drop
                    val sine = kotlin.math.sin(2 * kotlin.math.PI * freq * t)
                    
                    // heavily distorted noise for bone crushing
                    var noise = (Math.random() * 2 - 1) * 2.5
                    if (noise > 1.0) noise = 1.0 else if (noise < -1.0) noise = -1.0
                    
                    val value = ((sine + noise) * envelope * 32000).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }"""

replacement2 = """            SoundType.CRUNCH -> {
                val duration = 0.3f + Math.random() * 0.1f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = kotlin.math.exp(-12.0 * t) // decay
                    val freq = (60f + Math.random() * 40f) * kotlin.math.exp(-3.0 * t) // pitch drop
                    val sine = kotlin.math.sin(2 * kotlin.math.PI * freq * t)
                    
                    // heavily distorted noise for bone crushing
                    var noise = (Math.random() * 2 - 1) * 3.0
                    if (noise > 1.0) noise = 1.0 else if (noise < -1.0) noise = -1.0
                    
                    val value = ((sine * 0.4 + noise * 0.8) * envelope * 32767).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }"""

if target2 in content:
    content = content.replace(target2, replacement2)
    
with open("app/src/main/java/com/example/game/SoundSynth.kt", "w") as f:
    f.write(content)
print("Replaced successfully")
