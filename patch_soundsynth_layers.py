import re

with open("app/src/main/java/com/example/game/SoundSynth.kt", "r") as f:
    content = f.read()

target_compose = """        // ---- LAYER 2: DRONE STRINGS (level 2+) ----
        if (level >= 2) {
            writeDroneLayer(master, modeRoot, totalMs, rng, destiny, sampleRate)
        }

        // ---- LAYER 3: RECORDER MELODY (level 3+) ----
        if (level >= 3) {
            writeRecorderLayer(master, scale, markovMatrix, rng, stepMs, totalSteps, level, sampleRate)
        }

        // ---- LAYER 4: BODHRAN / WAR DRUM (level 4+) ----
        if (level >= 4) {
            writeBodhranLayer(master, stepMs, totalSteps, rng, destiny, sampleRate)
        }

        // ---- LAYER 5: HORN ORGANUM (level 5+) ----
        if (level >= 5) {
            writeHornLayer(master, scale, markovMatrix, rng, stepMs, totalSteps, destiny, sampleRate)
        }

        // ---- LAYER 6: PSALTERY COUNTER-MELODY (level 6+ / ORCHESTRAL destiny) ----
        if (level >= 6 && destiny == MusicalDestiny.ORCHESTRAL) {
            writePsalteryLayer(master, scale, markovMatrix, rng, stepMs, totalSteps, sampleRate)
        }"""

replacement_compose = """        // ---- LAYER 2: LUTE CHORDS (level 2+) ----
        if (level >= 2) {
            writeLuteLayer(master, scale, rng, stepMs, totalSteps, sampleRate)
        }

        // ---- LAYER 3: DRONE STRINGS (level 3+) ----
        if (level >= 3) {
            writeDroneLayer(master, modeRoot, totalMs, rng, destiny, sampleRate)
        }

        // ---- LAYER 4: BODHRAN / WAR DRUM (level 4+) ----
        if (level >= 4) {
            writeBodhranLayer(master, stepMs, totalSteps, rng, destiny, sampleRate)
        }

        // ---- LAYER 5: RECORDER MELODY (level 5+) ----
        if (level >= 5) {
            writeRecorderLayer(master, scale, markovMatrix, rng, stepMs, totalSteps, level, sampleRate)
        }

        // ---- LAYER 6: TAMBOURINE (level 6+) ----
        if (level >= 6) {
            writeTambourineLayer(master, stepMs, totalSteps, rng, sampleRate)
        }

        // ---- LAYER 7: HORN ORGANUM (level 7+) ----
        if (level >= 7) {
            writeHornLayer(master, scale, markovMatrix, rng, stepMs, totalSteps, destiny, sampleRate)
        }

        // ---- LAYER 8: PSALTERY COUNTER-MELODY (level 8+ / ORCHESTRAL destiny) ----
        if (level >= 8 && destiny == MusicalDestiny.ORCHESTRAL) {
            writePsalteryLayer(master, scale, markovMatrix, rng, stepMs, totalSteps, sampleRate)
        }"""

target_writers = """    private fun writeHarpLayer("""

replacement_writers = """    private fun writeLuteLayer(
        master: ShortArray, scale: FloatArray,
        rng: kotlin.random.Random, stepMs: Int, totalSteps: Int, sampleRate: Int
    ) {
        // Lute plays rhythmic arpeggiated chords
        val stepsPerBar = 8
        val numBars = totalSteps / stepsPerBar
        
        for (bar in 0 until numBars) {
            // Pick a chord root for this bar (I, IV, V, or VI)
            val rootDeg = listOf(0, 3, 4, 5).random(rng).coerceIn(0, scale.size - 1)
            val thirdDeg = (rootDeg + 2).coerceIn(0, scale.size - 1)
            val fifthDeg = (rootDeg + 4).coerceIn(0, scale.size - 1)
            
            val rFreq = scale[rootDeg]
            val tFreq = scale[thirdDeg]
            val fFreq = scale[fifthDeg]
            
            // Arpeggiate rhythmically: root on beat 0, fifth on beat 2, third on beat 4, octave on beat 6
            val barStartStep = bar * stepsPerBar
            
            val dur = stepMs * 2
            mix(master, luteNote(rFreq, dur, 0.8f, sampleRate), sampleOffset((barStartStep * stepMs).toFloat(), sampleRate), 0.7f)
            mix(master, luteNote(fFreq, dur, 0.6f, sampleRate), sampleOffset(((barStartStep + 2) * stepMs).toFloat(), sampleRate), 0.6f)
            mix(master, luteNote(tFreq, dur, 0.7f, sampleRate), sampleOffset(((barStartStep + 4) * stepMs).toFloat(), sampleRate), 0.65f)
            mix(master, luteNote(rFreq * 2f, dur, 0.6f, sampleRate), sampleOffset(((barStartStep + 6) * stepMs).toFloat(), sampleRate), 0.6f)
        }
    }

    private fun writeTambourineLayer(
        master: ShortArray, stepMs: Int, totalSteps: Int,
        rng: kotlin.random.Random, sampleRate: Int
    ) {
        // Tambourine plays on off-beats (steps 2, 6)
        for (i in 0 until totalSteps) {
            if (i % 4 == 2) {
                val startMs = (i * stepMs).toFloat()
                val vel = 0.5f + rng.nextFloat() * 0.3f
                mix(master, tambourineHit(stepMs, sampleRate, vel), sampleOffset(startMs, sampleRate), 0.8f)
            } else if (i % 4 == 0 && rng.nextFloat() < 0.2f) {
                // Occasional on-beat accent
                val startMs = (i * stepMs).toFloat()
                mix(master, tambourineHit(stepMs, sampleRate, 0.9f), sampleOffset(startMs, sampleRate), 0.9f)
            }
        }
    }

    private fun writeHarpLayer("""

if target_compose in content and target_writers in content:
    content = content.replace(target_compose, replacement_compose)
    content = content.replace(target_writers, replacement_writers)
    with open("app/src/main/java/com/example/game/SoundSynth.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
