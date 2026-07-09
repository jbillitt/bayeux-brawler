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
        // Increase dampening to make the harp less grating and less bright!
        val dampening = if (isLute) 0.45f else 0.485f 
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
        // Lower the base octave overall to make it less high pitched and grating!
        val rootMidiBases = listOf(45f, 38f, 40f, 43f) 
        var rootMidi = rootMidiBases.random(rng)
        
        val BPM = 75 + rng.nextInt(35)
        val msPerBeat = 60000 / BPM
        val measureMs = msPerBeat * 4 // 4/4 time
        
        val totalMeasures = 8 // Full progression length
        val totalMs = totalMeasures * measureMs
        val totalSamples = (sampleRate * (totalMs / 1000f)).toInt()
        
        val floatMaster = FloatArray(totalSamples)
        
        // The Passamezzo Antico chord progression (offsets from minor root):
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
        
        // Determine a consistent rhythmic and melodic theme for this seed
        // We will generate a sequence of durations (in fraction of a measure)
        // e.g., 0.25 = quarter note, 0.125 = eighth note
        val themeMotifs = listOf(
            listOf(0.25f, 0.25f, 0.5f), // Quarter, Quarter, Half
            listOf(0.125f, 0.125f, 0.25f, 0.5f), // Eighth run, quarter, half
            listOf(0.25f, 0.125f, 0.125f, 0.25f, 0.25f), // Quarter, two eighths, quarter, quarter
            listOf(0.5f, 0.125f, 0.125f, 0.125f, 0.125f), // Half note followed by an eighth run
            listOf(0.125f, 0.125f, 0.125f, 0.125f, 0.125f, 0.125f, 0.125f, 0.125f) // All eighths
        )
        val themeRhythm = themeMotifs.random(rng)
        
        // 1. Harp (Always) - Melodic theme
        for (m in 0 until totalMeasures) {
            val chord = progression[m]
            var mStartMs = m * measureMs.toFloat()
            var prevDegree = chord[0]
            
            for (dur in themeRhythm) {
                val noteMs = (measureMs * dur).toInt()
                
                // Melodic variance: sometimes step linearly (runs), sometimes play chord tones
                var degree = prevDegree
                if (rng.nextFloat() > 0.4f) {
                    // Jump to a chord tone
                    degree = chord.random(rng)
                } else {
                    // Passing run (step up or down)
                    degree += if (rng.nextBoolean()) 2 else -2
                }
                prevDegree = degree
                
                val freq = midiToFreq(rootMidi + degree + 12) // Harp is one octave up from bass
                val noteSamples = pluckNote(freq, noteMs, sampleRate, isLute = false)
                mix(floatMaster, (sampleRate * (mStartMs / 1000f)).toInt(), noteSamples, 0.65f)
                
                mStartMs += noteMs
            }
        }
        
        // 2. Lute (Level 2+) - Block chords on the downbeat
        if (level >= 2) {
            for (m in 0 until totalMeasures) {
                val chord = progression[m]
                val mStartMs = m * measureMs
                for ((idx, degree) in chord.withIndex()) {
                    val freq = midiToFreq(rootMidi + degree + 12)
                    val strumDelay = idx * 35 
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
                val freq1 = midiToFreq(rootMidi + chord[0]) // Low root
                val freq2 = midiToFreq(rootMidi + chord[2]) // Low 5th
                val drone1 = droneNote(freq1, measureMs, sampleRate)
                val drone2 = droneNote(freq2, measureMs, sampleRate)
                val offset = (sampleRate * (mStartMs / 1000f)).toInt()
                mix(floatMaster, offset, drone1, 0.35f)
                mix(floatMaster, offset, drone2, 0.35f)
            }
        }
        
        // 4. Bodhran Drum (Level 4+) - Heartbeat rhythm
        if (level >= 4) {
            for (m in 0 until totalMeasures) {
                val mStartMs = m * measureMs
                val beats = listOf(0, msPerBeat * 2, msPerBeat * 2 + msPerBeat / 2)
                for (b in beats) {
                    val drum = drumNote(500, sampleRate)
                    mix(floatMaster, (sampleRate * ((mStartMs + b) / 1000f)).toInt(), drum, 0.9f)
                }
            }
        }
        
        // 5. Recorder Melody (Level 5+) - Follows the chord progression smoothly
        if (level >= 5) {
            var lastDegree = progression[0][1]
            for (m in 0 until totalMeasures) {
                val chord = progression[m]
                val mStartMs = m * measureMs
                for (beat in 0 until 4) {
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
                    if (rng.nextFloat() > 0.7f) bestTone += if(rng.nextBoolean()) 2 else -2
                    lastDegree = bestTone
                    val freq = midiToFreq(rootMidi + bestTone + 24) // Flute is higher
                    val fl = fluteNote(freq, msPerBeat, sampleRate)
                    mix(floatMaster, (sampleRate * ((mStartMs + beat * msPerBeat) / 1000f)).toInt(), fl, 0.5f)
                }
            }
        }
        
        // 6. Trumpeter Ancillary Blast
        if (hasTrumpeter) {
            for (m in listOf(3, 7)) { // Blast at the V chords!
                val chord = progression[m]
                val freq = midiToFreq(rootMidi + chord[0] + 12)
                val horn = hornNote(freq, msPerBeat * 2, sampleRate)
                mix(floatMaster, (sampleRate * ((m * measureMs) / 1000f)).toInt(), horn, 0.7f)
            }
        }

        // Apply a gentle low-pass filter to remove harshness, and REVERB!
        val reverbDelaySamples = (sampleRate * 0.25f).toInt() // 250ms delay
        val reverbDecay = 0.4f
        
        val shortMaster = ShortArray(totalSamples)
        var lp = 0f
        var maxPeak = 0.01f
        for (i in 0 until totalSamples) {
            // Apply delay/reverb feedback
            if (i >= reverbDelaySamples) {
                floatMaster[i] += floatMaster[i - reverbDelaySamples] * reverbDecay
            }
            
            // Low pass filter
            lp += (floatMaster[i] - lp) * 0.35f 
            floatMaster[i] = lp
            
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
    print("SoundSynth overhauled successfully with themes and reverb!")
else:
    print("Failed to find bounds")
