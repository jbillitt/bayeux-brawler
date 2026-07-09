import re

with open('app/src/main/java/com/example/game/SoundSynth.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Find the start of ProceduralMedievalComposer
start_idx = code.find('object ProceduralMedievalComposer {')
if start_idx == -1:
    print("Could not find ProceduralMedievalComposer")
    exit(1)

# Find the end of ProceduralMedievalComposer (it ends right before object MedievalVocalizer)
end_idx = code.find('object MedievalVocalizer {')
if end_idx == -1:
    print("Could not find MedievalVocalizer")
    exit(1)

new_composer = '''object ProceduralMedievalComposer {
    var currentRootMidi: Float = 45f

    fun midiToFreq(midi: Float): Float = (440.0 * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()
    
    private fun mix(master: FloatArray, offset: Int, samples: FloatArray, volume: Float) {
        for (i in samples.indices) {
            if (offset + i < master.size) {
                master[offset + i] += samples[i] * volume
            }
        }
    }
    
    private fun pluckNote(freq: Float, durationMs: Int, sampleRate: Int, isLute: Boolean = false): FloatArray {
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val out = FloatArray(numSamples)
        if (freq <= 0f) return out
        val delayLen = (sampleRate / freq).toInt()
        if (delayLen <= 1) return out
        val delayLine = FloatArray(delayLen)
        for (i in 0 until delayLen) delayLine[i] = (Math.random() * 2.0 - 1.0).toFloat()
        var ptr = 0
        val dampening = if (isLute) 0.45f else 0.485f 
        for (i in 0 until numSamples) {
            val current = delayLine[ptr]
            val next = delayLine[(ptr + 1) % delayLen]
            val avg = (current + next) * dampening
            delayLine[ptr] = avg
            out[i] = avg
            ptr = (ptr + 1) % delayLen
        }
        val fadeOutSamples = (sampleRate * 0.1f).toInt()
        for (i in 0 until fadeOutSamples) {
            val idx = numSamples - 1 - i
            if (idx >= 0) out[idx] *= (i.toFloat() / fadeOutSamples)
        }
        return out
    }
    
    private fun fluteNote(freq: Float, durationMs: Int, sampleRate: Int): FloatArray {
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val out = FloatArray(numSamples)
        val attack = (sampleRate * 0.1f).toInt()
        val release = (sampleRate * 0.15f).toInt()
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val vibrato = 1.0 + 0.01 * kotlin.math.sin(2.0 * Math.PI * 5.0 * t)
            var wave = kotlin.math.sin(2.0 * Math.PI * freq * vibrato * t).toFloat()
            wave += (Math.random().toFloat() * 2f - 1f) * 0.05f
            val env = when {
                i < attack -> i.toFloat() / attack
                i > numSamples - release -> (numSamples - i).toFloat() / release
                else -> 1f
            }
            out[i] = wave * env
        }
        return out
    }
    
    private fun droneNote(freq: Float, durationMs: Int, sampleRate: Int): FloatArray {
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val out = FloatArray(numSamples)
        val attack = (sampleRate * 0.8f).toInt()
        val release = (sampleRate * 0.8f).toInt()
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            var wave = 0f
            for (h in 1..4) wave += (1f / h) * kotlin.math.sin(2.0 * Math.PI * freq * h * t).toFloat()
            val env = when {
                i < attack -> i.toFloat() / attack
                i > numSamples - release -> (numSamples - i).toFloat() / release
                else -> 1f
            }
            out[i] = wave * env * 0.4f
        }
        return out
    }
    
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

    fun compose(seed: Long, level: Int, hasTrumpeter: Boolean, sampleRate: Int): ShortArray {
        val rng = kotlin.random.Random(seed)
        
        val rootMidiBases = listOf(45f, 38f, 40f, 43f) 
        val rootMidi = rootMidiBases.random(rng)
        currentRootMidi = rootMidi
        
        // Varying tempo between playthroughs significantly (75 to 145 BPM)
        val BPM = 75 + rng.nextInt(70)
        val msPerBeat = 60000 / BPM
        val measureMs = msPerBeat * 4
        
        val totalMeasures = 16 // 16 bars for Verse + Chorus
        val totalMs = totalMeasures * measureMs
        val totalSamples = (sampleRate * (totalMs / 1000f)).toInt()
        val floatMaster = FloatArray(totalSamples)
        
        val progression = listOf(
            // Verse (Question and Answer) - 8 bars
            intArrayOf(0, 3, 7),     // 0: i
            intArrayOf(-2, 2, 5),    // 1: VII
            intArrayOf(0, 3, 7),     // 2: i
            intArrayOf(7, 11, 14),   // 3: V (Tension!)
            intArrayOf(3, 7, 10),    // 4: III
            intArrayOf(-2, 2, 5),    // 5: VII
            intArrayOf(0, 3, 7),     // 6: i
            intArrayOf(7, 11, 14),   // 7: V (Tension before chorus)
            // Chorus (Triumphant, Drops & Builds) - 8 bars
            intArrayOf(0, 3, 7),     // 8: i
            intArrayOf(5, 8, 12),    // 9: iv
            intArrayOf(-2, 2, 5),    // 10: VII
            intArrayOf(3, 7, 10),    // 11: III (Triumphant!)
            intArrayOf(5, 8, 12),    // 12: iv
            intArrayOf(0, 3, 7),     // 13: i
            intArrayOf(7, 11, 14),   // 14: V (Max Tension)
            intArrayOf(0, 3, 7)      // 15: i (Release, long note)
        )
        
        val scale = intArrayOf(-24, -22, -21, -19, -17, -16, -14, -12, -10, -9, -7, -5, -4, -2, 0, 2, 3, 5, 7, 8, 10, 11, 12, 14, 15, 17, 19, 20, 22, 23, 24, 26, 27, 29, 31, 32, 34, 36)
        
        // 1. Harp (Melody) - Question and Answer
        var prevDegree = 0
        for (m in 0 until totalMeasures) {
            val chord = progression[m]
            var mStartMs = m * measureMs.toFloat()
            
            // Sensically intermingle longer notes with musical theme
            val rhythm = when (m % 4) {
                0 -> listOf(0.5f, 0.25f, 0.25f) // Question start
                1 -> listOf(0.25f, 0.25f, 0.25f, 0.25f) // Moving
                2 -> listOf(0.5f, 0.5f) // Answer start
                else -> listOf(1.0f) // Measure 3, 7, 11, 15: Long note (Tension/Release)
            }
            
            for ((idx, dur) in rhythm.withIndex()) {
                val noteMs = (measureMs * dur).toInt()
                
                var degree = prevDegree
                if (dur == 1.0f) {
                    // Land on a chord tone for long notes
                    degree = chord[0]
                    if (m >= 8) degree += 12 // Triumphant: octave up for Chorus!
                } else {
                    var scaleIdx = scale.indexOfFirst { it >= degree }
                    if (scaleIdx == -1) scaleIdx = scale.size / 2
                    // Question (m%4 < 2) moves up, Answer (m%4 == 2) moves down
                    val dir = if (m % 4 < 2) 1 else -1
                    scaleIdx += dir * (if (rng.nextBoolean()) 1 else 2)
                    scaleIdx = scaleIdx.coerceIn(0, scale.size - 1)
                    degree = scale[scaleIdx]
                }
                prevDegree = degree
                
                val freq = midiToFreq(rootMidi + degree + 12)
                val noteSamples = pluckNote(freq, noteMs, sampleRate, isLute = false)
                mix(floatMaster, (sampleRate * (mStartMs / 1000f)).toInt(), noteSamples, 0.65f)
                
                mStartMs += noteMs
            }
        }
        
        // 2. Lute (Level 2+)
        if (level >= 2) {
            for (m in 0 until totalMeasures) {
                // Drop: Lute rests on measure 8 (start of chorus)
                if (m == 8) continue
                val chord = progression[m]
                val mStartMs = m * measureMs
                for ((idx, degree) in chord.withIndex()) {
                    val freq = midiToFreq(rootMidi + degree + 12)
                    val noteSamples = pluckNote(freq, measureMs, sampleRate, isLute = true)
                    mix(floatMaster, (sampleRate * ((mStartMs + idx * 35) / 1000f)).toInt(), noteSamples, 0.5f)
                }
            }
        }
        
        // 3. Drone Strings (Level 3+)
        if (level >= 3) {
            for (m in 0 until totalMeasures) {
                // Drop: Strings rest on measure 8 and 9
                if (m == 8 || m == 9) continue
                val chord = progression[m]
                val freq1 = midiToFreq(rootMidi + chord[0]) 
                val freq2 = midiToFreq(rootMidi + chord[2]) 
                val drone1 = droneNote(freq1, measureMs, sampleRate)
                val drone2 = droneNote(freq2, measureMs, sampleRate)
                val offset = (sampleRate * (m * measureMs / 1000f)).toInt()
                mix(floatMaster, offset, drone1, 0.35f)
                mix(floatMaster, offset, drone2, 0.35f)
            }
        }
        
        // 4. Bodhran Drum (Level 4+) - Power of the Pulse
        if (level >= 4) {
            for (m in 0 until totalMeasures) {
                // Drop in measure 8!
                if (m == 8) continue
                
                val mStartMs = m * measureMs
                val beats = mutableListOf(0, msPerBeat * 2) // Strong downbeat and beat 3 (Pulse!)
                
                // Build: extra drums in measures 12-14
                if (m in 12..14) {
                    beats.add(msPerBeat)
                    beats.add(msPerBeat * 3)
                }
                
                for (b in beats) {
                    val drum = drumNote(500, sampleRate)
                    // Emphasize downbeat!
                    val vol = if (b == 0) 1.2f else 0.8f
                    mix(floatMaster, (sampleRate * ((mStartMs + b) / 1000f)).toInt(), drum, vol)
                }
            }
        }
        
        // 5. Recorder Melody (Level 5+)
        if (level >= 5) {
            for (m in 0 until totalMeasures) {
                // Recorder rests on long note measures to let harp shine
                if (m % 4 == 3) continue
                val chord = progression[m]
                val mStartMs = m * measureMs
                for (beat in 0 until 4) {
                    val degree = chord.random(rng)
                    val freq = midiToFreq(rootMidi + degree + 24)
                    val fl = fluteNote(freq, msPerBeat, sampleRate)
                    mix(floatMaster, (sampleRate * ((mStartMs + beat * msPerBeat) / 1000f)).toInt(), fl, 0.4f)
                }
            }
        }
        
        // 6. Trumpeter Ancillary Blast
        if (hasTrumpeter) {
            for (m in listOf(3, 7, 14, 15)) { // Blast at the V chords and final I!
                val chord = progression[m]
                val freq = midiToFreq(rootMidi + chord[0] + 12)
                val dur = if (m == 15) measureMs * 2 else measureMs
                val horn = hornNote(freq, dur, sampleRate)
                mix(floatMaster, (sampleRate * ((m * measureMs) / 1000f)).toInt(), horn, 0.7f)
            }
        }

        val reverbDelaySamples = (sampleRate * 0.25f).toInt()
        val reverbDecay = 0.15f
        
        val shortMaster = ShortArray(totalSamples)
        var lp = 0f
        var maxPeak = 0.01f
        for (i in 0 until totalSamples) {
            if (i >= reverbDelaySamples) {
                floatMaster[i] += floatMaster[i - reverbDelaySamples] * reverbDecay
            }
            lp += (floatMaster[i] - lp) * 0.35f 
            floatMaster[i] = lp
            val v = kotlin.math.abs(lp)
            if (v > maxPeak) maxPeak = v
        }
        
        val gain = 30000f / maxPeak
        for (i in 0 until totalSamples) {
            shortMaster[i] = (floatMaster[i] * gain).toInt().coerceIn(-32768, 32767).toShort()
        }
        
        return shortMaster
    }
}
'''
code = code[:start_idx] + new_composer + code[end_idx:]

with open('app/src/main/java/com/example/game/SoundSynth.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("ProceduralMedievalComposer fully revamped!")
