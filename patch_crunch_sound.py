import re

with open("app/src/main/java/com/example/game/SoundSynth.kt", "r") as f:
    content = f.read()

target1 = """enum class SoundType {
    CLANG,   // Shield block / metallic strike
    THWACK,  // Soft tissue hit / blunt force
    SWOOSH,  // Weapon swing or flying projectile
    OUCH,    // Comedic pain screech
    HUZZAH   // Victory trumpet fanfare
}"""

replacement1 = """enum class SoundType {
    CLANG,   // Shield block / metallic strike
    THWACK,  // Soft tissue hit / blunt force
    SWOOSH,  // Weapon swing or flying projectile
    OUCH,    // Comedic pain screech
    HUZZAH,  // Victory trumpet fanfare
    CRUNCH   // Bone breaking / heavy smash
}"""

target2 = """            SoundType.THWACK -> {
                val duration = 0.18f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = (1.0f - t / duration)
                    val freq = 160f - (100f * (t / duration))
                    val sine = kotlin.math.sin(2 * kotlin.math.PI * freq * t)
                    val noise = (Math.random() * 2 - 1) * 0.1
                    val value = ((sine + noise) / 1.1 * envelope * 30000).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }"""

replacement2 = """            SoundType.THWACK -> {
                val duration = 0.18f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = (1.0f - t / duration)
                    val freq = 160f - (100f * (t / duration))
                    val sine = kotlin.math.sin(2 * kotlin.math.PI * freq * t)
                    val noise = (Math.random() * 2 - 1) * 0.1
                    val value = ((sine + noise) / 1.1 * envelope * 30000).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }
            SoundType.CRUNCH -> {
                val duration = 0.25f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = kotlin.math.exp(-15.0 * t) // fast decay
                    val freq = 80f * kotlin.math.exp(-5.0 * t) // pitch drop
                    val sine = kotlin.math.sin(2 * kotlin.math.PI * freq * t)
                    
                    // heavily distorted noise for bone crushing
                    var noise = (Math.random() * 2 - 1) * 0.6
                    noise = 1.5 * noise - 0.5 * noise * noise * noise // crude saturation
                    
                    // some higher frequency artifacts for snapping bones
                    val snap = if (t < 0.05f) (Math.random() * 2 - 1) * 0.4 else 0.0
                    
                    val value = ((sine + noise + snap) * envelope * 32000).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }"""

if target1 in content:
    content = content.replace(target1, replacement1)
    
if "SoundType.THWACK -> {" in content:
    # Need to match the block properly since we might have imported math differently
    # Let's just use re.sub
    pattern = r"SoundType\.THWACK -> \{.*?\n            \}"
    match = re.search(pattern, content, re.DOTALL)
    if match:
        content = content[:match.start()] + replacement2 + content[match.end():]
        with open("app/src/main/java/com/example/game/SoundSynth.kt", "w") as f:
            f.write(content)
        print("Replaced successfully")
    else:
        print("Target2 not found.")
else:
    print("Target1 not found.")
