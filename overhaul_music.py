import re

with open('app/src/main/java/com/example/game/SoundSynth.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Isolate the ProceduralMedievalComposer object block
start_idx = code.find("object ProceduralMedievalComposer {")
end_idx = code.find("object MedievalVocalizer {")

if start_idx != -1 and end_idx != -1:
    new_composer = '''object ProceduralMedievalComposer {

    private fun midiToFreq(midi: Float): Float = (440.0 * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()
    
    // Improved synthesis tools
    private fun mix(master: FloatArray, offset: Int, samples: FloatArray, volume: Float) {
        for (i in samples.indices) {
            if (offset + i < master.size) {
                master[offset + i] += samples[i] * volume
            }
        }
    }
    
    // Karplus-Strong string synthesis for realistic Harp / Lute
    private fun pluckNote(freq: Float, durationMs: Int, sampleRate: Int, isLute: Boolean = false): FloatArray {
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val out = FloatArray(numSamples)
        if (freq <= 0f) return out
        
        val delayLen = (sampleRate / freq).toInt()
        if (delayLen <= 1) return out
        
        val delayLine = FloatArray(delayLen)
        // Fill delay line with noise
        for (i in 0 until delayLen) {
            delayLine[i] = (Math.random() * 2.0 - 1.0).toFloat()
        }
        
        var ptr = 0
        val dampening = if (isLute) 0.4f else 0.49f // Lute decays faster than Harp
        for (i in 0 until numSamples) {
            val current = delayLine[ptr]
            val next = delayLine[(ptr + 1) % delayLen]
            // Low-pass filter (averaging)
            val avg = (current + next) * dampening
            delayLine[ptr] = avg
            out[i] = avg
            ptr = (ptr + 1) % delayLen
        }
        
        // Apply envelope to avoid clicking at the end
        val fadeOutSamples = (sampleRate * 0.1f).toInt()
        for (i in 0 until fadeOutSamples) {
            val idx = numSamples - 1 - i
            if (idx >= 0) out[idx] *= (i.toFloat() / fadeOutSamples)
        }
        return out
    }
    
    // Smooth Sine wave for recorder with vibrato
    private fun fluteNote(freq: Float, durationMs: Int, sampleRate: Int): FloatArray {
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val out = FloatArray(numSamples)
        val attack = (sampleRate * 0.1f).toInt()
        val release = (sampleRate * 0.15f).toInt()
        
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val vibrato = 1.0 + 0.01 * kotlin.math.sin(2.0 * Math.PI * 5.0 * t)
            var wave = kotlin.math.sin(2.0 * Math.PI * freq * vibrato * t).toFloat()
            // Add air noise
            wave += (Math.random().toFloat() * 2f - 1f) * 0.05f
            
            // ADSR
            val env = when {
                i < attack -> i.toFloat() / attack
                i > numSamples - release -> (numSamples - i).toFloat() / release
                else -> 1f
            }
            out[i] = wave * env
        }
        return out
    }
    
    // Bowed string drone (sawtooth / rich harmonic)
    private fun droneNote(freq: Float, durationMs: Int, sampleRate: Int): FloatArray {
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val out = FloatArray(numSamples)
        val attack = (sampleRate * 0.8f).toInt()
        val release = (sampleRate * 0.8f).toInt()
        
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            // Sawtooth-like by summing odd/even harmonics
            var wave = 0f
            for (h in 1..4) {
                wave += (1f / h) * kotlin.math.sin(2.0 * Math.PI * freq * h * t).toFloat()
            }
            val env = when {
                i < attack -> i.toFloat() / attack
                i > numSamples - release -> (numSamples - i).toFloat() / release
                else -> 1f
            }
            out[i] = wave * env * 0.4f
        }
        return out
    }
    
    // Bodhran (frame drum)
    private fun drumNote(durationMs: Int, sampleRate: Int): FloatArray {
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val out = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val env = kotlin.math.exp(-15.0 * t).toFloat()
            val freq = 60f * kotlin.math.exp(-5.0 * t).toFloat()
            val boom = kotlin.math.sin(2.0 * Math.PI * freq * t).toFloat()
            val noise = (Math.random().toFloat() * 2f - 1f) * 0.5f * kotlin.math.exp(-30.0 * t).toFloat()
            out[i] = (boom + noise) * env
        }
        return out
    }
    
    // Brass horn
    private fun hornNote(freq: Float, durationMs: Int, sampleRate: Int): FloatArray {
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val out = FloatArray(numSamples)
        val attack = (sampleRate * 0.1f).toInt()
        val release = (sampleRate * 0.2f).toInt()
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            var wave = 0f
            for (h in 1..6) wave += (1f / h) * kotlin.math.sin(2.0 * Math.PI * freq * h * t).toFloat()
            val env = when {
                i < attack -> i.toFloat() / attack
                i > numSamples - release -> (numSamples - i).toFloat() / release
                else -> 1f
            }
            out[i] = wave * env * 0.5f
        }
        return out
    }

    // Roman numeral chords (Passamezzo Antico standard progression): i - VII - i - V - III - VII - i - V
    // Root offsets in semitones for minor key: 0(i), -2(VII), 3(III), 7(V)
    
    fun compose(seed: Long, level: Int, hasTrumpeter: Boolean, sampleRate: Int): ShortArray {
        val rng = kotlin.random.Random(seed)
        
        // 1. Pick a key and octave based on seed!
        // Minor keys: A, D, E, G
        val rootMidiBases = listOf(57f, 50f, 52f, 55f) 
        var rootMidi = rootMidiBases.random(rng)
        
        // Random octave shift down or up
        val octaveShift = listOf(-12f, 0f, 12f).random(rng)
        rootMidi += octaveShift

        val BPM = 80 + rng.nextInt(40)
        val msPerBeat = 60000 / BPM
        val measureMs = msPerBeat * 4 // 4/4 time
        
        val totalMeasures = 8 // Full progression length
        val totalMs = totalMeasures * measureMs
        val totalSamples = (sampleRate * (totalMs / 1000f)).toInt()
        
        val floatMaster = FloatArray(totalSamples)
        
        // The Passamezzo Antico chord progression (offsets from minor root):
        // m0 (i), m1 (VII), m2 (i), m3 (V), m4 (III), m5 (VII), m6 (i), m7 (V)
        val progression = listOf(
            intArrayOf(0, 3, 7),     // i
            intArrayOf(-2, 2, 5),    // VII
            intArrayOf(0, 3, 7),     // i
            intArrayOf(7, 11, 14),   // V (major V)
            intArrayOf(3, 7, 10),    // III
            intArrayOf(-2, 2, 5),    // VII
            intArrayOf(0, 3, 7),     // i
            intArrayOf(7, 11, 14)    // V
        )

        // 1. Harp (Always) - Arpeggiated chords
        for (m in 0 until totalMeasures) {
            val chord = progression[m]
            val mStartMs = m * measureMs
            // Arpeggiate 8th notes (8 notes per measure)
            val noteMs = msPerBeat / 2
            for (i in 0 until 8) {
                // Pick a note from the chord, sometimes jumping an octave
                val degree = chord[i % chord.size] + (if (rng.nextBoolean()) 0 else 12)
                val freq = midiToFreq(rootMidi + degree)
                val noteSamples = pluckNote(freq, noteMs * 2, sampleRate, isLute = false)
                mix(floatMaster, (sampleRate * ((mStartMs + i * noteMs) / 1000f)).toInt(), noteSamples, 0.4f)
            }
        }
        
        // 2. Lute (Level 2+) - Block chords on the downbeat
        if (level >= 2) {
            for (m in 0 until totalMeasures) {
                val chord = progression[m]
                val mStartMs = m * measureMs
                // Strumming the chord
                for ((idx, degree) in chord.withIndex()) {
                    val freq = midiToFreq(rootMidi + degree)
                    val strumDelay = idx * 25 // 25ms delay per string
                    val noteSamples = pluckNote(freq, measureMs, sampleRate, isLute = true)
                    mix(floatMaster, (sampleRate * ((mStartMs + strumDelay) / 1000f)).toInt(), noteSamples, 0.5f)
                }
            }
        }
        
        // 3. Drone Strings (Level 3+) - Sustained root and 5th
        if (level >= 3) {
            for (m in 0 until totalMeasures) {
                val chord = progression[m]
                val mStartMs = m * measureMs
                val freq1 = midiToFreq(rootMidi + chord[0] - 12) // Low root
                val freq2 = midiToFreq(rootMidi + chord[2] - 12) // Low 5th
                val drone1 = droneNote(freq1, measureMs, sampleRate)
                val drone2 = droneNote(freq2, measureMs, sampleRate)
                val offset = (sampleRate * (mStartMs / 1000f)).toInt()
                mix(floatMaster, offset, drone1, 0.25f)
                mix(floatMaster, offset, drone2, 0.25f)
            }
        }
        
        // 4. Bodhran Drum (Level 4+) - Heartbeat rhythm
        if (level >= 4) {
            for (m in 0 until totalMeasures) {
                val mStartMs = m * measureMs
                // Boom ... Boom-Boom ...
                val beats = listOf(0, msPerBeat * 2, msPerBeat * 2 + msPerBeat / 2)
                for (b in beats) {
                    val drum = drumNote(400, sampleRate)
                    mix(floatMaster, (sampleRate * ((mStartMs + b) / 1000f)).toInt(), drum, 0.7f)
                }
            }
        }
        
        // 5. Recorder Melody (Level 5+) - Follows the chord progression smoothly
        if (level >= 5) {
            var lastDegree = progression[0][1] // Start on 3rd
            for (m in 0 until totalMeasures) {
                val chord = progression[m]
                val mStartMs = m * measureMs
                // 4 quarter notes
                for (beat in 0 until 4) {
                    // Smooth voice leading: find chord tone closest to lastDegree
                    var bestTone = chord[0]
                    var minDiff = 999
                    for (ct in chord) {
                        for (oct in listOf(-12, 0, 12)) {
                            val candidate = ct + oct
                            val diff = kotlin.math.abs(candidate - lastDegree)
                            if (diff < minDiff) {
                                minDiff = diff
                                bestTone = candidate
                            }
                        }
                    }
                    // Occasionally step away as a passing tone
                    if (rng.nextFloat() > 0.7f) bestTone += if(rng.nextBoolean()) 2 else -2
                    
                    lastDegree = bestTone
                    val freq = midiToFreq(rootMidi + bestTone + 12) // higher octave
                    val fl = fluteNote(freq, msPerBeat, sampleRate)
                    mix(floatMaster, (sampleRate * ((mStartMs + beat * msPerBeat) / 1000f)).toInt(), fl, 0.5f)
                }
            }
        }
        
        // 6. Trumpeter Ancillary Blast
        if (hasTrumpeter) {
            for (m in listOf(3, 7)) { // Blast at the V chords!
                val chord = progression[m]
                val freq = midiToFreq(rootMidi + chord[0]) // Root of V
                val horn = hornNote(freq, msPerBeat * 2, sampleRate)
                mix(floatMaster, (sampleRate * ((m * measureMs) / 1000f)).toInt(), horn, 0.8f)
            }
        }

        // Apply a gentle low-pass filter to remove harshness, and basic compression
        val shortMaster = ShortArray(totalSamples)
        var lp = 0f
        var maxPeak = 0.01f
        for (i in 0 until totalSamples) {
            lp += (floatMaster[i] - lp) * 0.4f // low pass
            val v = kotlin.math.abs(lp)
            if (v > maxPeak) maxPeak = v
        }
        // Normalize and convert
        val gain = 30000f / maxPeak
        for (i in 0 until totalSamples) {
            var s = floatMaster[i] * gain
            shortMaster[i] = s.toInt().coerceIn(-32768, 32767).toShort()
        }
        
        return shortMaster
    }
}
'''
    code = code[:start_idx] + new_composer + code[end_idx:]
    with open('app/src/main/java/com/example/game/SoundSynth.kt', 'w', encoding='utf-8') as f:
        f.write(code)
    print("SoundSynth overhauled successfully!")
else:
    print("Failed to find bounds")
