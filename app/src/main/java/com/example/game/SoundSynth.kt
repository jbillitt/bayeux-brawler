package com.example.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Sound effects (unchanged)
// ---------------------------------------------------------------------------

enum class SoundType {
    CLANG,   // Shield block / metallic strike
    THWACK,  // Soft tissue hit / blunt force
    SWOOSH,  // Weapon swing or flying projectile
    OUCH,    // Comedic pain screech
    HUZZAH,  // Victory trumpet fanfare
    CRUNCH,  // Bone breaking / heavy smash
    VICTORY_FANFARE // 1.5s victory sting
}

object MedievalAudioSynth {
    private const val SAMPLE_RATE = 22050
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun playSound(type: SoundType) {
        scope.launch {
            try {
                val buffer = generateBuffer(type)
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                val durationMs = (buffer.size.toFloat() / SAMPLE_RATE * 1000).toLong()
                Thread.sleep(durationMs + 50)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateBuffer(type: SoundType): ShortArray {
        return when (type) {
            SoundType.CLANG -> {
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
                        SoundType.THWACK -> {
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
            }
            SoundType.SWOOSH -> {
                val duration = 0.2f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                var lastNoise = 0.0
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = sin(PI * (t / duration))
                    val noise = (Math.random() * 2 - 1)
                    val alpha = 0.2
                    val currentNoise = alpha * noise + (1.0 - alpha) * lastNoise
                    lastNoise = currentNoise
                    val value = (currentNoise * envelope * 18000).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }
            SoundType.OUCH -> {
                val duration = 0.3f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = sin(PI * (t / duration)).toFloat() * (1.0f - t / duration)
                    val f = 150f - 60f * (t / duration)
                    val waveVal = if ((t * f) % 1.0f < 0.5f) 1.0f else -1.0f
                    val noise = (Math.random().toFloat() * 2f - 1f) * 0.4f
                    val formant = sin(2 * PI * 600 * t).toFloat() * 0.5f
                    val sampleVal = (waveVal + noise + formant) * envelope * 18000
                    samples[i] = sampleVal.toInt().coerceIn(-32768, 32767).toShort()
                }
                samples
            }
            SoundType.HUZZAH -> {
                val duration = 1.0f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val noteDuration = duration / 3.2f
                    val noteIndex = (t / noteDuration).toInt().coerceIn(0, 2)
                    val root = ProceduralMedievalComposer.currentRootMidi + 12f // 1 octave up
                    val baseFreq = when (noteIndex) {
                        0 -> ProceduralMedievalComposer.midiToFreq(root)
                        1 -> ProceduralMedievalComposer.midiToFreq(root + 3f) // minor 3rd
                        else -> ProceduralMedievalComposer.midiToFreq(root + 7f) // perfect 5th
                    }
                    val localT = t % noteDuration
                    val noteEnvelope = sin(PI * (localT / noteDuration))
                    val wave = sin(2 * PI * baseFreq * t) + 0.6 * sin(2 * PI * 2 * baseFreq * t) + 0.3 * sin(2 * PI * 3 * baseFreq * t)
                    val value = (wave / 1.8 * noteEnvelope * 22000).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }
            SoundType.VICTORY_FANFARE -> {
                val duration = 1.5f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                val root = ProceduralMedievalComposer.currentRootMidi + 12f
                val notes = listOf(
                    ProceduralMedievalComposer.midiToFreq(root),
                    ProceduralMedievalComposer.midiToFreq(root + 3f), // minor 3rd
                    ProceduralMedievalComposer.midiToFreq(root + 7f), // perfect 5th
                    ProceduralMedievalComposer.midiToFreq(root + 12f) // octave
                )
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val noteDuration = duration / 4.0f
                    val noteIndex = (t / noteDuration).toInt().coerceIn(0, 3)
                    val baseFreq = notes[noteIndex]
                    val localT = (t % noteDuration).toDouble()
                    
                    val hornAttack = 0.04
                    val hornRelease = 0.08
                    val hornEnv = when {
                        localT < hornAttack -> localT / hornAttack
                        localT > noteDuration - hornRelease -> (noteDuration - localT) / hornRelease
                        else -> 1.0
                    }.coerceIn(0.0, 1.0)
                    var hornWave = 0.0
                    for (h in 1..5) hornWave += (1.0 / h) * sin(2 * PI * h * baseFreq * t)
                    
                    val harpEnv = if (localT < 0.003) (localT / 0.003) else exp(-2.5 * (localT - 0.003))
                    val fD = baseFreq.toDouble()
                    val harpWave = (sin(2 * PI * fD * t)
                        + 0.45 * sin(2 * PI * 2 * fD * t) * exp(-3.0 * t)
                        + 0.25 * sin(2 * PI * 3 * fD * t) * exp(-5.0 * t)
                        + 0.12 * sin(2 * PI * 4 * fD * t) * exp(-8.0 * t))
                    val harpNoise = if (localT < 0.015) (Math.random() * 2.0 - 1.0) * 0.18 * (1.0 - localT / 0.015) else 0.0
                    
                    val value = ((hornWave * hornEnv * 5000.0) + ((harpWave + harpNoise) * harpEnv * 7000.0)).toInt()
                    samples[i] = value.coerceIn(-32768, 32767).toShort()
                }
                samples
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PROCEDURAL MEDIEVAL MUSIC ENGINE
//
// Architecture:
//  - Seed-based: each new game generates a unique seed → deterministic composition
//  - Markov chain melody generation using Church mode (Dorian or Mixolydian)
//  - Instruments layer in as level increases
//  - Arc: Sad solo harp → drone strings → recorder → bodhran → horn organum → fanfare/orchestral
//  - Two "destiny" paths seeded at game start: FANFARE or ORCHESTRAL (Greensleeves-style sweep)
// ---------------------------------------------------------------------------

object MedievalHarpPlayer {

    private const val SAMPLE_RATE = 22050

    // ---- State ----
    var isPlaying = false
        private set
    private var audioTrack: AudioTrack? = null
    private var currentLevel = -1
    private var currentHasTrumpeter = false
    private var gameSeed: Long = System.currentTimeMillis()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ---- Public API ----

    /** Call at the start of each new game to fix the seed for this run */
    fun newGame(seed: Long = System.currentTimeMillis()) {
        gameSeed = seed
        currentLevel = -1 // force rebuild even if same level
    }

    fun startMusic(level: Int = 1, hasTrumpeter: Boolean = false, moods: List<String> = emptyList()) {
        if (isPlaying && currentLevel == level && currentHasTrumpeter == hasTrumpeter) return
        if (isPlaying) stopMusic()

        currentLevel = level
        currentHasTrumpeter = hasTrumpeter
        isPlaying = true

        scope.launch {
            try {
                val buffer = ProceduralMedievalComposer.compose(
                    seed      = gameSeed,
                    level     = level,
                    hasTrumpeter = hasTrumpeter,
                    sampleRate = SAMPLE_RATE,
                    moods     = moods
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.setLoopPoints(0, buffer.size, -1)
                track.play()
                audioTrack = track
            } catch (e: Exception) {
                e.printStackTrace()
                isPlaying = false
            }
        }
    }

    fun stopMusic() {
        if (!isPlaying) return
        isPlaying = false
        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioTrack = null
        }
    }
}

// ---------------------------------------------------------------------------
// COMPOSER ENGINE
// ---------------------------------------------------------------------------

object ProceduralMedievalComposer {
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
            val vibrato = 1.0 + 0.003 * kotlin.math.sin(2.0 * Math.PI * 5.0 * t)
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

    private fun tambourineHit(sampleRate: Int): FloatArray {
        val durationMs = 80
        val numSamples = (sampleRate * durationMs / 1000)
        return FloatArray(numSamples) { i ->
            val env = (1f - i.toFloat() / numSamples)  // linear decay
            (Random.nextFloat() * 2f - 1f) * env * 0.7f
        }
    }

    fun compose(seed: Long, level: Int, hasTrumpeter: Boolean, sampleRate: Int, moods: List<String> = emptyList()): ShortArray {
        val rng = kotlin.random.Random(seed)
        
        val rootMidiBases = listOf(45f, 38f, 40f, 43f, 41f, 36f)
        val rootMidi = rootMidiBases.random(rng)
        currentRootMidi = rootMidi

        // Mood modifiers (applied before tempo so finalBpm drives secPerBeat)
        val moodRng = kotlin.random.Random(seed + 999L)
        val baseBPM = 68 + rng.nextInt(90) // 68–157 BPM
        val finalBpm = if ("More Tempo" in moods && moodRng.nextFloat() < 0.8f) baseBPM + 18 else baseBPM
        val brighterMode = "Happier" in moods && moodRng.nextFloat() < 0.75f
        val mournfulMode = "Mournful" in moods && moodRng.nextFloat() < 0.75f
        val bassBoost = if ("More Bass" in moods && moodRng.nextFloat() < 0.8f) 1.4f else 1.0f
        val complexDrums = "Complex Drums" in moods && moodRng.nextFloat() < 0.85f
        val moreFanfares = "More Fanfares" in moods && moodRng.nextFloat() < 0.7f

        val BPM = finalBpm
        val msPerBeat = 60000 / BPM
        val secPerBeat = msPerBeat / 1000f
        val measureMs = msPerBeat * 4

        // Per-run variance
        val sparseness = rng.nextFloat() // 0..1; >0.6 skips some off-beat notes
        val harVolMult = 0.7f + rng.nextFloat() * 0.6f
        val luteVolMult = 0.7f + rng.nextFloat() * 0.6f
        val recVolMult  = 0.7f + rng.nextFloat() * 0.6f
        val droneVolMult = 0.7f + rng.nextFloat() * 0.6f
        val octaveShift = rng.nextInt(3) - 1  // -1, 0, or +1 octave for harp melody

        // Destiny path
        val destiny = if (rng.nextBoolean()) "FANFARE" else "ORCHESTRAL"

        val totalMeasures = 16 // 16 bars for Verse + Chorus
        val totalBars = totalMeasures
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
                
                val freq = midiToFreq(rootMidi + degree + 12 + octaveShift * 12)
                val noteSamples = pluckNote(freq, noteMs, sampleRate, isLute = false)
                // humanization jitter (2-8ms)
                val harpJitter = rng.nextInt(6) + 2
                val harpStart = ((sampleRate * (mStartMs / 1000f)).toInt() + harpJitter * sampleRate / 1000).coerceIn(0, floatMaster.size - noteSamples.size)
                mix(floatMaster, harpStart, noteSamples, 0.65f * harVolMult)

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
                    // sparseness: skip inner chord tones on off-beats when sparse
                    if (sparseness > 0.6f && idx > 0 && rng.nextFloat() < (sparseness - 0.6f) * 2f) continue
                    val freq = midiToFreq(rootMidi + degree + 12)
                    val noteSamples = pluckNote(freq, measureMs, sampleRate, isLute = true)
                    // humanization jitter (2-8ms)
                    val luteJitter = rng.nextInt(6) + 2
                    val luteStart = ((sampleRate * ((mStartMs + idx * 35) / 1000f)).toInt() + luteJitter * sampleRate / 1000).coerceIn(0, floatMaster.size - noteSamples.size)
                    mix(floatMaster, luteStart, noteSamples, 0.5f * luteVolMult)
                }
            }
        }
        
        // 3. Drone Strings (Level 3+)
        if (level >= 3) {
            val droneVol = 0.35f * droneVolMult * bassBoost
            for (m in 0 until totalMeasures) {
                // Drop: Strings rest on measure 8 and 9
                if (m == 8 || m == 9) continue
                val chord = progression[m]
                val freq1 = midiToFreq(rootMidi + chord[0])
                val freq2 = midiToFreq(rootMidi + chord[2])
                val drone1 = droneNote(freq1, measureMs, sampleRate)
                val drone2 = droneNote(freq2, measureMs, sampleRate)
                val offset = (sampleRate * (m * measureMs / 1000f)).toInt()
                mix(floatMaster, offset, drone1, droneVol)
                mix(floatMaster, offset, drone2, droneVol)
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
                    // sparseness: skip some beats when sparse
                    if (sparseness > 0.6f && beat % 2 == 1 && rng.nextFloat() < (sparseness - 0.6f) * 2.5f) continue
                    val degree = chord.random(rng)
                    val freq = midiToFreq(rootMidi + degree + 24)
                    val fl = fluteNote(freq, msPerBeat, sampleRate)
                    // humanization jitter (2-8ms)
                    val recJitter = rng.nextInt(6) + 2
                    val recStart = ((sampleRate * ((mStartMs + beat * msPerBeat) / 1000f)).toInt() + recJitter * sampleRate / 1000).coerceIn(0, floatMaster.size - fl.size)
                    mix(floatMaster, recStart, fl, 0.4f * recVolMult)
                }
            }
        }
        
        // 6. Trumpeter Ancillary Blast
        if (hasTrumpeter) {
            val trumpetMeasures = if (moreFanfares)
                listOf(3, 7, 11, 14, 15)
            else
                listOf(3, 7, 14, 15)
            for (m in trumpetMeasures) {
                if (m >= totalMeasures) continue
                val chord = progression[m]
                val freq = midiToFreq(rootMidi + chord[0] + 12)
                val dur = if (m == 15) measureMs * 2 else measureMs
                val horn = hornNote(freq, dur, sampleRate)
                mix(floatMaster, (sampleRate * ((m * measureMs) / 1000f)).toInt(), horn, 0.7f)
            }
        }

        // 7. Tambourine on off-beats (Level 6+)
        if (level >= 6) {
            for (bar in 0 until totalBars) {
                for (beat in listOf(0.5f, 1.5f, 2.5f, 3.5f)) {
                    if (sparseness > 0.6f && rng.nextFloat() < (sparseness - 0.6f)) continue
                    val offsetSec = (bar * 4 + beat) * secPerBeat
                    val noteBuffer = tambourineHit(sampleRate)
                    val startSample = (offsetSec * sampleRate).toInt().coerceIn(0, floatMaster.size - noteBuffer.size)
                    for (i in noteBuffer.indices) {
                        if (startSample + i < floatMaster.size) floatMaster[startSample + i] += noteBuffer[i] * 0.4f
                    }
                }
            }
        }

        // 8. Horn Organum — sustained 5ths (Level 7+)
        if (level >= 7) {
            val chordDurMs = (secPerBeat * 4 * 1000).toInt()
            for (bar in 0 until totalBars step 4) {
                val chordIdx = (bar / 4) % progression.size
                val rootMidiInt = (rootMidi + progression[chordIdx][0]).toInt()
                val fifthMidi = rootMidiInt + 7
                val t = bar * 4 * secPerBeat
                val rootNote = hornNote(midiToFreq(rootMidiInt.toFloat()), chordDurMs, sampleRate)
                val fifthNote = hornNote(midiToFreq(fifthMidi.toFloat()), chordDurMs, sampleRate)
                val startSample = (t * sampleRate).toInt().coerceIn(0, floatMaster.size - rootNote.size)
                for (i in rootNote.indices) {
                    if (startSample + i < floatMaster.size) {
                        floatMaster[startSample + i] += rootNote[i] * 0.35f + fifthNote[i] * 0.25f
                    }
                }
            }
        }

        // 9. Destiny climax (Level 8+)
        if (level >= 8) {
            when (destiny) {
                "ORCHESTRAL" -> {
                    // Psaltery: high arpeggiated plucks sweeping the progression
                    val psalteryVol = 0.3f
                    for (bar in 0 until totalBars) {
                        val chordIdx = (bar / 4) % progression.size
                        val rootMidiInt = (rootMidi + progression[chordIdx][0]).toInt()
                        for (beat in 0 until 4) {
                            val arpeggioNote = rootMidiInt + listOf(0, 4, 7, 12)[beat % 4]
                            val freq = midiToFreq((arpeggioNote + 12).toFloat())
                            val t = (bar * 4 + beat) * secPerBeat
                            val noteDurMs = (secPerBeat * 800).toInt()
                            val note = pluckNote(freq, noteDurMs, sampleRate, isLute = false)
                            val startSample = (t * sampleRate).toInt().coerceIn(0, floatMaster.size - note.size)
                            for (i in note.indices) {
                                if (startSample + i < floatMaster.size) floatMaster[startSample + i] += note[i] * psalteryVol
                            }
                        }
                    }
                }
                "FANFARE" -> {
                    // Extra harp fanfare sweeps at phrase ends (every 8 bars)
                    for (bar in 7 until totalBars step 8) {
                        val chordIdx = (bar / 4) % progression.size
                        val rootMidiInt = (rootMidi + progression[chordIdx][0]).toInt()
                        for (n in 0 until 6) {
                            val noteM = rootMidiInt + listOf(0, 4, 7, 12, 16, 19)[n]
                            val freq = midiToFreq((noteM + 12).toFloat())
                            val t = bar * 4 * secPerBeat + n * 0.07
                            val note = pluckNote(freq, 400, sampleRate, isLute = false)
                            val startSample = (t * sampleRate).toInt().coerceIn(0, floatMaster.size - note.size)
                            for (i in note.indices) {
                                if (startSample + i < floatMaster.size) floatMaster[startSample + i] += note[i] * 0.45f
                            }
                        }
                    }
                }
            }
        }

        // 10. Mood: brighter — shift root up 3 semitones (already baked via brighterMode flag; apply as post-note-selection root shift)
        // Note: brighterMode/mournfulMode affect future melody reharmonisation; here we add
        // a subtle extra layer: complexDrums adds upbeat hits on existing drum beats.
        if (complexDrums && level >= 4) {
            for (m in 0 until totalMeasures) {
                if (m == 8) continue
                val mStartMs = m * measureMs
                // upbeat hits (beat 2 and 4) at half volume
                for (upbeat in listOf(msPerBeat, msPerBeat * 3)) {
                    val drum = drumNote(300, sampleRate)
                    mix(floatMaster, (sampleRate * ((mStartMs + upbeat) / 1000f)).toInt(), drum, 0.5f)
                }
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
object MedievalVocalizer {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    val result = tts?.setLanguage(java.util.Locale.forLanguageTag("la"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(java.util.Locale.US)
                    }
                    tts?.setPitch(0.85f)
                    tts?.setSpeechRate(1.25f)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speak(text: String) {
        if (!isInitialized) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "medieval_vocal_bark")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shutdown() {
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isInitialized = false
        tts = null
    }
}