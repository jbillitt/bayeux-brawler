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

    data class MotifNote(val degree: Int, val duration: Float)

    fun generateMotif(rng: kotlin.random.Random, is3_4: Boolean): List<MotifNote> {
        val rhythmPatterns = if (is3_4) {
            listOf(
                listOf(1.0f, 1.0f, 1.0f),
                listOf(1.5f, 0.5f, 1.0f),
                listOf(0.5f, 0.5f, 1.0f, 1.0f),
                listOf(1.0f, 0.5f, 0.5f, 1.0f)
            )
        } else {
            listOf(
                listOf(1.0f, 1.0f, 1.0f, 1.0f),
                listOf(1.5f, 0.5f, 1.0f, 1.0f),
                listOf(0.5f, 0.5f, 1.0f, 2.0f),
                listOf(1.0f, 1.0f, 2.0f)
            )
        }
        val rhythm = rhythmPatterns.random(rng)
        
        // Modal motion: mostly stepwise
        val contours = listOf(
            listOf(0, 1, 2, 1, 0), // rise and fall
            listOf(0, -1, 0, 1, 2), // dip then rise
            listOf(2, 1, 0, -1, 0), // fall and return
            listOf(0, 2, 3, 2, 0) // minor 3rd arpeggio-ish
        )
        val contour = contours.random(rng)
        
        val degreeOffsets = rhythm.indices.map { i -> 
            if (i < contour.size) contour[i] else (contour.last() + rng.nextInt(-1, 2))
        }

        return rhythm.indices.map { MotifNote(degreeOffsets[it], rhythm[it]) }
    }

    fun phraseFromMotif(motif: List<MotifNote>, type: String, rng: kotlin.random.Random): List<MotifNote> {
        return when (type) {
            "A" -> motif // Strict repeat
            "A'" -> {
                // Variant ending
                val variant = motif.toMutableList()
                if (variant.isNotEmpty()) {
                    val last = variant.last()
                    variant[variant.lastIndex] = last.copy(degree = last.degree - 1)
                }
                variant
            }
            "B" -> {
                // Sequence up a step or 3rd
                val shift = listOf(1, 2).random(rng)
                motif.map { it.copy(degree = it.degree + shift) }
            }
            else -> motif
        }
    }

    fun midiToFreq(midi: Float): Float = (440.0 * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()
    
    private fun mix(master: FloatArray, offset: Int, samples: FloatArray, volume: Float) {
        for (i in samples.indices) {
            if (offset + i < master.size) {
                master[offset + i] += samples[i] * volume
            }
        }
    }
    
    private val harpCache = mutableMapOf<Int, FloatArray>()
    private val luteCache = mutableMapOf<Int, FloatArray>()

    private fun ensureCache(sampleRate: Int) {
        if (harpCache.isNotEmpty()) return
        for (midi in 30..85) {
            val freq = midiToFreq(midi.toFloat())
            harpCache[midi] = bakeKarplusStrong(freq, sampleRate, false)
            luteCache[midi] = bakeKarplusStrong(freq, sampleRate, true)
        }
    }

    private fun shape(buf: FloatArray, k: Float) {
        val deg = 20.0f * Math.PI.toFloat() / 180.0f
        for (i in buf.indices) {
            val x = buf[i].coerceIn(-1f, 1f)
            buf[i] = (3.0f + k) * x * deg / (Math.PI.toFloat() + k * Math.abs(x))
        }
    }

    fun lowpass1Pole(buf: FloatArray, cutoff: Float, sampleRate: Int) {
        val dt = 1.0f / sampleRate
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoff)
        val alpha = dt / (rc + dt)
        var prev = 0f
        for (i in buf.indices) {
            prev = prev + alpha * (buf[i] - prev)
            buf[i] = prev
        }
    }

    private fun bakeKarplusStrong(freq: Float, sampleRate: Int, isLute: Boolean): FloatArray {
        val numSamples = sampleRate * 2 // bake 2 seconds
        val out = FloatArray(numSamples)
        if (freq <= 0f) return out
        val delayLen = (sampleRate / freq).toInt()
        if (delayLen <= 1) return out
        val delayLine = FloatArray(delayLen)
        for (i in 0 until delayLen) delayLine[i] = (Math.random() * 2.0 - 1.0).toFloat()
        var ptr = 0
        val dampening = if (isLute) 0.485f else 0.496f 
        for (i in 0 until numSamples) {
            val current = delayLine[ptr]
            val next = delayLine[(ptr + 1) % delayLen]
            val avg = (current + next) * dampening
            delayLine[ptr] = avg
            out[i] = avg
            ptr = (ptr + 1) % delayLen
        }
        return out
    }
    
    private fun pluckNote(freq: Float, durationMs: Int, sampleRate: Int, isLute: Boolean = false): FloatArray {
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val out = FloatArray(numSamples)
        if (freq <= 0f) return out
        
        val midi = (12.0 * (Math.log(freq / 440.0) / Math.log(2.0)) + 69).toInt().coerceIn(30, 85)
        val cache = if (isLute) luteCache else harpCache
        val cached = cache[midi] ?: return out
        
        val copyLen = numSamples.coerceAtMost(cached.size)
        System.arraycopy(cached, 0, out, 0, copyLen)
        
        // Fast fade out at end of note bounds to prevent pop
        val fadeOutSamples = (sampleRate * 0.03f).toInt().coerceAtMost(copyLen)
        for (i in 0 until fadeOutSamples) {
            val idx = copyLen - 1 - i
            out[idx] *= (i.toFloat() / fadeOutSamples)
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
            (kotlin.random.Random.nextFloat() * 2f - 1f) * env * 0.7f
        }
    }

    fun compose(seed: Long, level: Int, hasTrumpeter: Boolean, sampleRate: Int, moods: List<String> = emptyList()): ShortArray {
        val rng = kotlin.random.Random(seed)
        
        val rootMidiBases = listOf(45f, 38f, 40f, 43f, 41f, 36f)
        val rootMidi = rootMidiBases.random(rng)
        currentRootMidi = rootMidi

        val is3_4 = rng.nextBoolean()
        val beatsPerBar = if (is3_4) 3 else 4
        
        // Clamp BPM to 62-88 (medieval range)
        val baseBPM = 62 + rng.nextInt(27) 
        val finalBpm = if ("More Tempo" in moods) baseBPM + 10 else baseBPM
        
        val BPM = finalBpm
        val msPerBeat = 60000 / BPM
        val secPerBeat = msPerBeat / 1000f
        val measureMs = msPerBeat * beatsPerBar

        val totalBars = 16
        val totalMs = totalBars * measureMs
        
        ensureCache(sampleRate)
        val totalSamples = (sampleRate * (totalMs / 1000f)).toInt()
        val floatMaster = FloatArray(totalSamples)

        // Dorian mode (the most medieval-sounding scale)
        val dorianScale = intArrayOf(-10, -8, -7, -5, -3, -1, 0, 2, 3, 5, 7, 9, 10, 12, 14, 15, 17, 19, 21, 22, 24)
        // Center the scale index so motif degree 0 is root
        val rootIdx = dorianScale.indexOf(0).takeIf { it >= 0 } ?: 6
        
        val motif = generateMotif(rng, is3_4)
        
        // Instrument Layering (randomized entry)
        val melodicPool = mutableListOf("harp", "lute", "recorder")
        val inst1 = melodicPool.removeAt(rng.nextInt(melodicPool.size))
        val inst2 = if (level >= 2) melodicPool.removeAt(rng.nextInt(melodicPool.size)) else ""
        val inst3 = if (level >= 5) melodicPool[0] else ""

        val droneVol = 0.35f
        val harVolMult = 0.8f
        val luteVolMult = 0.8f
        val recVolMult = 0.8f

        // AABA phrase structure
        for (bar in 0 until totalBars) {
            val barStartMs = bar * measureMs
            val section = bar / 4
            val barInSection = bar % 4
            
            // AABA: Section 0, 1, 3 are A. Section 2 is B.
            val phraseType = if (section == 2) "B" else if (barInSection % 2 == 1) "A'" else "A"
            val phraseNotes = phraseFromMotif(motif, phraseType, rng)

            // Melody
            var currentMs = barStartMs.toFloat()
            for (note in phraseNotes) {
                val degreeIdx = (rootIdx + note.degree).coerceIn(0, dorianScale.size - 1)
                val midiOffset = dorianScale[degreeIdx]
                val freq = midiToFreq(rootMidi + midiOffset + 24)
                val noteMs = (note.duration * msPerBeat).toInt()
                val offsetSamples = (sampleRate * (currentMs / 1000f)).toInt()
                
                // Inst 1 (Level 1+)
                if (inst1 == "harp") {
                    val s = pluckNote(freq, noteMs, sampleRate, isLute = false)
                    mix(floatMaster, offsetSamples, s, 0.6f * harVolMult)
                } else if (inst1 == "lute") {
                    val s = pluckNote(freq, noteMs, sampleRate, isLute = true)
                    mix(floatMaster, offsetSamples, s, 0.6f * luteVolMult)
                } else if (inst1 == "recorder") {
                    val s = fluteNote(freq, noteMs, sampleRate)
                    mix(floatMaster, offsetSamples, s, 0.4f * recVolMult)
                }
                
                // Double of inst 1 on Level 6+
                if (level >= 6) {
                    val doubleFreq = midiToFreq(rootMidi + midiOffset + 24)
                    if (inst1 == "harp") {
                        val s = pluckNote(doubleFreq, noteMs, sampleRate, isLute = false)
                        mix(floatMaster, offsetSamples + (sampleRate * 0.01f).toInt(), s, 0.4f * harVolMult)
                    } else if (inst1 == "lute") {
                        val s = pluckNote(doubleFreq, noteMs, sampleRate, isLute = true)
                        mix(floatMaster, offsetSamples + (sampleRate * 0.01f).toInt(), s, 0.4f * luteVolMult)
                    } else if (inst1 == "recorder") {
                        val s = fluteNote(doubleFreq, noteMs, sampleRate)
                        mix(floatMaster, offsetSamples + (sampleRate * 0.01f).toInt(), s, 0.3f * recVolMult)
                    }
                }

                // Inst 2 (Level 2+)
                if (level >= 2) {
                    // harmony a third down or unison
                    val harmonyIdx = (degreeIdx - 2).coerceIn(0, dorianScale.size - 1)
                    val hFreq = midiToFreq(rootMidi + dorianScale[harmonyIdx] + 24)
                    if (inst2 == "harp") {
                        val s = pluckNote(hFreq, noteMs, sampleRate, isLute = false)
                        mix(floatMaster, offsetSamples, s, 0.4f * harVolMult)
                    } else if (inst2 == "lute") {
                        val s = pluckNote(hFreq, noteMs, sampleRate, isLute = true)
                        mix(floatMaster, offsetSamples, s, 0.4f * luteVolMult)
                    } else if (inst2 == "recorder") {
                        val s = fluteNote(hFreq, noteMs, sampleRate)
                        mix(floatMaster, offsetSamples, s, 0.3f * recVolMult)
                    }
                }
                
                // Inst 3 (Level 5+)
                if (level >= 5) {
                    val octFreq = midiToFreq(rootMidi + midiOffset + 36)
                    if (inst3 == "harp") {
                        val s = pluckNote(octFreq, noteMs, sampleRate, isLute = false)
                        mix(floatMaster, offsetSamples, s, 0.4f * harVolMult)
                    } else if (inst3 == "lute") {
                        val s = pluckNote(octFreq, noteMs, sampleRate, isLute = true)
                        mix(floatMaster, offsetSamples, s, 0.4f * luteVolMult)
                    } else if (inst3 == "recorder") {
                        val s = fluteNote(octFreq, noteMs, sampleRate)
                        mix(floatMaster, offsetSamples, s, 0.3f * recVolMult)
                    }
                }

                currentMs += note.duration * msPerBeat
            }

            // Drone (Level 3+)
            if (level >= 3) {
                if (barInSection == 0) { // drone every 4 bars for 4 bars long
                    val dFreq1 = midiToFreq(rootMidi)
                    val dFreq2 = midiToFreq(rootMidi + 7)
                    val dSamples = (measureMs * 4)
                    val drone1 = droneNote(dFreq1, dSamples, sampleRate)
                    val drone2 = droneNote(dFreq2, dSamples, sampleRate)
                    val offset = (sampleRate * (barStartMs / 1000f)).toInt()
                    mix(floatMaster, offset, drone1, droneVol)
                    mix(floatMaster, offset, drone2, droneVol)
                }
            }

            // Bodhran (Level 4+)
            if (level >= 4) {
                // Downbeat drum
                val drum = drumNote(400, sampleRate)
                mix(floatMaster, (sampleRate * (barStartMs / 1000f)).toInt(), drum, 1.0f)
                if (!is3_4) {
                    // Beat 3 drum
                    mix(floatMaster, (sampleRate * ((barStartMs + msPerBeat * 2) / 1000f)).toInt(), drum, 0.6f)
                }
            }
            
            // Tambourine (Level 6+)
            if (level >= 6) {
                for (b in 1 until beatsPerBar) {
                    val t = tambourineHit(sampleRate)
                    mix(floatMaster, (sampleRate * ((barStartMs + msPerBeat * b) / 1000f)).toInt(), t, 0.3f)
                }
            }
            
            // Horn Organum (Level 7+)
            if (level >= 7 && barInSection == 0) {
                val hFreq1 = midiToFreq(rootMidi + 12)
                val hFreq2 = midiToFreq(rootMidi + 19)
                val hSamples = (measureMs * 4)
                val horn1 = hornNote(hFreq1, hSamples, sampleRate)
                val horn2 = hornNote(hFreq2, hSamples, sampleRate)
                val offset = (sampleRate * (barStartMs / 1000f)).toInt()
                mix(floatMaster, offset, horn1, 0.2f)
                mix(floatMaster, offset, horn2, 0.15f)
            }
            
            // Destiny / Trumpeter
            if (hasTrumpeter && barInSection == 3) {
                val freq = midiToFreq(rootMidi + 12)
                val horn = hornNote(freq, measureMs, sampleRate)
                mix(floatMaster, (sampleRate * (barStartMs / 1000f)).toInt(), horn, 0.5f)
            }
        }

        // Destiny climax level 8+
        if (level >= 8) {
            val destiny = if (rng.nextBoolean()) "FANFARE" else "ORCHESTRAL"
            if (destiny == "ORCHESTRAL") {
                // Psaltery sweeping arpeggios
                for (bar in 0 until totalBars) {
                    val t = bar * measureMs / 1000f
                    for (beat in 0 until beatsPerBar) {
                        val arpeggioNote = rootMidi + listOf(0, 3, 7, 10, 12)[beat % 5] + 12
                        val freq = midiToFreq(arpeggioNote)
                        val note = pluckNote(freq, 600, sampleRate, isLute = false)
                        val startSample = (sampleRate * (t + beat * secPerBeat)).toInt()
                        mix(floatMaster, startSample, note, 0.25f)
                    }
                }
            } else {
                // Fanfare at end of phrases
                for (bar in listOf(3, 7, 11, 15)) {
                    val t = bar * measureMs / 1000f
                    for (n in 0 until 4) {
                        val noteM = rootMidi + listOf(0, 7, 12, 19)[n] + 12
                        val freq = midiToFreq(noteM)
                        val note = pluckNote(freq, 400, sampleRate, isLute = false)
                        val startSample = (sampleRate * (t + n * 0.1)).toInt()
                        mix(floatMaster, startSample, note, 0.35f)
                    }
                }
            }
        }

        val reverbDelaySamples = (sampleRate * 0.25f).toInt()
        val reverbDecay = 0.15f
        
        val shortMaster = ShortArray(totalSamples)
        var maxPeak = 0.01f
        for (i in 0 until totalSamples) {
            if (i >= reverbDelaySamples) {
                floatMaster[i] += floatMaster[i - reverbDelaySamples] * reverbDecay
            }
            // Removed double lowpass, just peak detect
            val v = kotlin.math.abs(floatMaster[i])
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