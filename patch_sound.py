import re

with open('app/src/main/java/com/example/game/SoundSynth.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Fix Harp Pedal Point to follow chords
pedal_old = '''        // Always add a slow bass pedal point (drone low note) on the beat
        // This grounds the harmony in an authentic medieval way
        val bassFreq = scale[0] / 2f // one octave below root
        for (beat in 0 until (totalSteps / 4)) {
            val startMs = beat * 4 * stepMs.toFloat()
            val bassVelocity = 0.9f + rng.nextFloat() * 0.15f
            mix(master, harpNote(bassFreq, stepMs * 4, bassVelocity, sampleRate),
                sampleOffset(startMs, sampleRate), 0.7f)
        }'''

pedal_new = '''        // Always add a slow bass pedal point (drone low note) on the beat
        // This grounds the harmony in an authentic medieval way, now following the chord progression!
        for (beat in 0 until (totalSteps / 4)) {
            val startMs = beat * 4 * stepMs.toFloat()
            val currentBar = (beat * 4 / 8).coerceIn(0, progression.size - 1)
            val currentChordRoot = progression[currentBar % progression.size]
            val bassFreq = scale[currentChordRoot.coerceIn(0, scale.size - 1)] / 2f // one octave below chord root
            
            val bassVelocity = 0.95f + rng.nextFloat() * 0.15f
            mix(master, harpNote(bassFreq, stepMs * 4, bassVelocity, sampleRate),
                sampleOffset(startMs, sampleRate), 1.2f) // Boosted volume
        }'''
code = code.replace(pedal_old, pedal_new)

# 2. Re-distribute the layers so the new system is immediately obvious
layer2_old = '''        // ---- LAYER 2: LUTE CHORDS (level 2+) ----
        if (level >= 2) {'''
layer2_new = '''        // ---- LAYER 2: LUTE CHORDS (level 1+) ----
        if (level >= 1) { // Shifted to level 1 so chord progression is obvious immediately!'''
code = code.replace(layer2_old, layer2_new)

layer3_old = '''        // ---- LAYER 3: DRONE STRINGS (level 3+) ----
        if (level >= 3) {'''
layer3_new = '''        // ---- LAYER 3: DRONE STRINGS (level 2+) ----
        if (level >= 2) {'''
code = code.replace(layer3_old, layer3_new)

layer4_old = '''        // ---- LAYER 4: BODHRAN / WAR DRUM (level 4+) ----
        if (level >= 4) {'''
layer4_new = '''        // ---- LAYER 4: BODHRAN / WAR DRUM (level 3+) ----
        if (level >= 3) {'''
code = code.replace(layer4_old, layer4_new)

layer5_old = '''        // ---- LAYER 5: RECORDER MELODY (level 5+) ----
        if (level >= 5) {'''
layer5_new = '''        // ---- LAYER 5: RECORDER MELODY (level 4+) ----
        if (level >= 4) {'''
code = code.replace(layer5_old, layer5_new)

with open('app/src/main/java/com/example/game/SoundSynth.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("Sound patched!")
