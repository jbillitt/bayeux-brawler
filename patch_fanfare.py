import re

with open('app/src/main/java/com/example/game/SoundSynth.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Make midiToFreq public
code = code.replace('private fun midiToFreq', 'fun midiToFreq')

# Add currentRootMidi to ProceduralMedievalComposer
code = code.replace('object ProceduralMedievalComposer {', 'object ProceduralMedievalComposer {\n    var currentRootMidi: Float = 45f\n')

# Set currentRootMidi in compose
old_compose_root = '''        val rootMidiBases = listOf(45f, 38f, 40f, 43f) 
        var rootMidi = rootMidiBases.random(rng)'''
new_compose_root = '''        val rootMidiBases = listOf(45f, 38f, 40f, 43f) 
        var rootMidi = rootMidiBases.random(rng)
        currentRootMidi = rootMidi'''
code = code.replace(old_compose_root, new_compose_root)

# Patch HUZZAH
old_huzzah = '''                    val noteIndex = (t / noteDuration).toInt().coerceIn(0, 2)
                    val baseFreq = when (noteIndex) {
                        0 -> 261.63f
                        1 -> 349.23f
                        else -> 440.00f
                    }'''
new_huzzah = '''                    val noteIndex = (t / noteDuration).toInt().coerceIn(0, 2)
                    val root = ProceduralMedievalComposer.currentRootMidi + 12f // 1 octave up
                    val baseFreq = when (noteIndex) {
                        0 -> ProceduralMedievalComposer.midiToFreq(root)
                        1 -> ProceduralMedievalComposer.midiToFreq(root + 3f) // minor 3rd
                        else -> ProceduralMedievalComposer.midiToFreq(root + 7f) // perfect 5th
                    }'''
code = code.replace(old_huzzah, new_huzzah)

# Patch VICTORY_FANFARE
old_victory = '''            SoundType.VICTORY_FANFARE -> {
                val duration = 1.5f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                val notes = listOf(261.63f, 329.63f, 392.00f, 523.25f)'''
new_victory = '''            SoundType.VICTORY_FANFARE -> {
                val duration = 1.5f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                val root = ProceduralMedievalComposer.currentRootMidi + 12f
                val notes = listOf(
                    ProceduralMedievalComposer.midiToFreq(root),
                    ProceduralMedievalComposer.midiToFreq(root + 3f), // minor 3rd
                    ProceduralMedievalComposer.midiToFreq(root + 7f), // perfect 5th
                    ProceduralMedievalComposer.midiToFreq(root + 12f) // octave
                )'''
code = code.replace(old_victory, new_victory)

with open('app/src/main/java/com/example/game/SoundSynth.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("Audio fanfares patched to match key!")
