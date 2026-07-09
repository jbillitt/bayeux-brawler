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
    HUZZAH   // Victory trumpet fanfare
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
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val envelope = (1.0f - t / duration) * (1.0f - t / duration)
                    val sine = sin(2 * PI * 880 * t) + 0.6 * sin(2 * PI * 1320 * t) + 0.4 * sin(2 * PI * 1760 * t)
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
                    val sine = sin(2 * PI * freq * t)
                    val noise = (Math.random() * 2 - 1) * 0.1
                    val value = ((sine + noise) / 1.1 * envelope * 30000).toInt()
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
                    val baseFreq = when (noteIndex) {
                        0 -> 261.63f
                        1 -> 349.23f
                        else -> 440.00f
                    }
                    val localT = t % noteDuration
                    val noteEnvelope = sin(PI * (localT / noteDuration))
                    val wave = sin(2 * PI * baseFreq * t) + 0.6 * sin(2 * PI * 2 * baseFreq * t) + 0.3 * sin(2 * PI * 3 * baseFreq * t)
                    val value = (wave / 1.8 * noteEnvelope * 22000).toInt()
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

    fun startMusic(level: Int = 1, hasTrumpeter: Boolean = false) {
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
                    sampleRate = SAMPLE_RATE
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


    // ---- Church Modes ----
    // Intervals in semitones from root, two octaves
    private val DORIAN_INTERVALS    = intArrayOf(0, 2, 3, 5, 7, 9, 10, 12, 14, 15, 17, 19, 21, 22, 24)
    private val MIXOLYDIAN_INTERVALS= intArrayOf(0, 2, 4, 5, 7, 9, 10, 12, 14, 16, 17, 19, 21, 22, 24)

    // MIDI note numbers for reference (C3 = 48, D3 = 50, etc.)
    // We map modal scale degrees to frequencies
    private fun midiToFreq(midi: Int): Float = (440f * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()

    // D Dorian root = MIDI 50 (D3)
    // G Mixolydian root = MIDI 55 (G3)
    private fun buildModeScale(rootMidi: Int, intervals: IntArray): FloatArray =
        intervals.map { midiToFreq(rootMidi + it) }.toFloatArray()

    // ---- Destiny paths ----
    enum class MusicalDestiny { FANFARE, ORCHESTRAL }

    // ---- Markov chain for melody ----
    // Each scale degree has weighted transitions to other scale degrees
    // Favour stepwise motion (±1), allow 4th/5th leaps (±3, ±4), punish large jumps
    // The bias array shape: [from_degree][to_degree] = weight (unnormalised)
    private fun buildMelodyTransitions(scaleSize: Int, rng: Random, destiny: MusicalDestiny): Array<FloatArray> {
        val matrix = Array(scaleSize) { FloatArray(scaleSize) { 0f } }
        for (from in 0 until scaleSize) {
            for (to in 0 until scaleSize) {
                val dist = abs(to - from)
                val weight = when (dist) {
                    0    -> 0.05f                        // unison (slight repetition chance)
                    1    -> 3.5f + rng.nextFloat() * 1.5f // stepwise – heavily favoured
                    2    -> 1.2f + rng.nextFloat() * 0.8f
                    3, 4 -> 1.0f + rng.nextFloat() * 1.0f // 4th/5th leaps – allowed
                    5    -> 0.3f
                    else -> 0.05f                         // large leap – rare
                }
                // FANFARE pushes upward leaps; ORCHESTRAL pushes descending arch
                val directionBias = when {
                    destiny == MusicalDestiny.FANFARE && to > from     -> 1.3f
                    destiny == MusicalDestiny.ORCHESTRAL && to < from  -> 1.2f
                    else                                                -> 1.0f
                }
                matrix[from][to] = weight * directionBias
            }
        }
        return matrix
    }

    private fun nextMarkovDegree(current: Int, matrix: Array<FloatArray>, rng: Random): Int {
        val weights = matrix[current]
        val total = weights.sum()
        var roll = rng.nextFloat() * total
        for (i in weights.indices) {
            roll -= weights[i]
            if (roll <= 0f) return i
        }
        return weights.size - 1
    }

    // ---- Rhythmic mode generation ----
    // Returns list of (durationMs, isRest) pairs for one phrase
    private fun generateRhythmicPhrase(stepMs: Int, stepsInPhrase: Int, rng: Random, level: Int): List<Pair<Int, Boolean>> {
        val result = mutableListOf<Pair<Int, Boolean>>()
        var remaining = stepsInPhrase
        // At low levels: slow, free rhythm (longer notes). Higher levels: more pulse.
        val minNoteSteps = if (level <= 2) 2 else 1
        val maxNoteSteps = if (level <= 2) 6 else if (level <= 4) 4 else 3
        val restChance    = if (level <= 2) 0.18f else 0.08f

        while (remaining > 0) {
            val actualMin = min(minNoteSteps, remaining)
            val steps = (actualMin..min(maxNoteSteps, remaining)).random(rng)
            val isRest = rng.nextFloat() < restChance
            result.add(Pair(steps * stepMs, isRest))
            remaining -= steps
        }
        return result
    }

    // ---- PCM sample generators ----

    private fun harpNote(freq: Float, durationMs: Int, velocity: Float, sampleRate: Int): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val fD = freq.toDouble()
        val velD = velocity.toDouble()
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Sharp pluck attack then exponential decay
            val envelope = if (t < 0.003) (t / 0.003) else exp(-2.5 * (t - 0.003))
            // Harp timbre: fundamental + falling harmonics (plucked string model)
            val wave = (sin(2 * PI * fD * t)
                + 0.45 * sin(2 * PI * 2 * fD * t) * exp(-3.0 * t)
                + 0.25 * sin(2 * PI * 3 * fD * t) * exp(-5.0 * t)
                + 0.12 * sin(2 * PI * 4 * fD * t) * exp(-8.0 * t))
            // Pluck transient noise
            val noise = if (t < 0.015) (Math.random() * 2.0 - 1.0) * 0.18 * (1.0 - t / 0.015) else 0.0
            samples[i] = ((wave + noise) * envelope * velD * 7000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    private fun droneNote(freq: Float, durationMs: Int, sampleRate: Int, amplitude: Float = 0.25f): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val fD = freq.toDouble(); val ampD = amplitude.toDouble()
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val fadeIn  = (t / 0.3).coerceAtMost(1.0)
            val fadeOut = ((numSamples - i).toDouble() / (sampleRate * 0.3)).coerceAtMost(1.0)
            val env = fadeIn * fadeOut
            val wave = sin(2 * PI * fD * t) * 0.7 + sin(2 * PI * 2 * fD * t) * 0.2
            samples[i] = (wave * env * ampD * 28000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    private fun recorderNote(freq: Float, durationMs: Int, sampleRate: Int, velocity: Float = 0.9f): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val fD = freq.toDouble(); val velD = velocity.toDouble()
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val dur = durationMs / 1000.0
            val attack = 0.04; val release = 0.06
            val env = when {
                t < attack        -> t / attack
                t > dur - release -> (dur - t) / release
                else              -> 1.0
            }.coerceIn(0.0, 1.0)
            // Gentle vibrato (5 Hz) – recorder/flute character
            val vibrato = 1.0 + 0.012 * sin(2 * PI * 5.0 * t)
            val wave = sin(2 * PI * fD * vibrato * t) + 0.1 * sin(2 * PI * 3 * fD * vibrato * t)
            val breath = (Math.random() * 2.0 - 1.0) * 0.04
            samples[i] = ((wave + breath) * env * velD * 4200.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    private fun bodhranBeat(durationMs: Int, sampleRate: Int, accent: Float = 1f): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val accentD = accent.toDouble()
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = exp(-9.0 * t) * accentD
            val freqSweep = 130.0 * exp(-14.0 * t) + 40.0
            val drum = sin(2 * PI * freqSweep * t)
            val noise = (Math.random() * 2.0 - 1.0) * 0.3
            samples[i] = ((drum + noise) * env * 22000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    private fun hornNote(freq: Float, durationMs: Int, sampleRate: Int, velocity: Float = 1f): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val fD = freq.toDouble(); val velD = velocity.toDouble()
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val dur = durationMs / 1000.0
            val attack = 0.04; val release = 0.08
            val env = when {
                t < attack        -> t / attack
                t > dur - release -> (dur - t) / release
                else              -> 1.0
            }.coerceIn(0.0, 1.0)
            // Brassy sawtooth with harmonics
            var wave = 0.0
            for (h in 1..5) wave += (1.0 / h) * sin(2 * PI * h * fD * t)
            samples[i] = (wave * env * velD * 5000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    private fun psalterNote(freq: Float, durationMs: Int, sampleRate: Int): ShortArray {
        // Psaltery: bright plucked string, higher harmonics than harp
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val fD = freq.toDouble()
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = if (t < 0.002) (t / 0.002) else exp(-3.5 * (t - 0.002))
            val wave = (sin(2 * PI * fD * t)
                + 0.6 * sin(2 * PI * 2 * fD * t) * exp(-2.0 * t)
                + 0.4 * sin(2 * PI * 3 * fD * t) * exp(-4.0 * t)
                + 0.2 * sin(2 * PI * 4 * fD * t) * exp(-7.0 * t)
                + 0.1 * sin(2 * PI * 5 * fD * t) * exp(-11.0 * t))
            samples[i] = (wave * env * 5500.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // ---- Master buffer mixer ----

    private fun mix(master: ShortArray, voice: ShortArray, startSample: Int, vol: Float) {
        for (i in voice.indices) {
            val dst = startSample + i
            if (dst >= master.size) break
            val sum = master[dst] + (voice[i] * vol).toInt()
            master[dst] = sum.coerceIn(-32768, 32767).toShort()
        }
    }

    private fun sampleOffset(ms: Float, sampleRate: Int): Int = (ms / 1000f * sampleRate).toInt()

    // ---- MAIN COMPOSITION ENTRY POINT ----

    fun compose(seed: Long, level: Int, hasTrumpeter: Boolean, sampleRate: Int): ShortArray {
        val rng = Random(seed)

        // Choose destiny from the seed (50/50, fixed for the game run)
        val destiny = if (rng.nextBoolean()) MusicalDestiny.FANFARE else MusicalDestiny.ORCHESTRAL

        // Choose mode: low levels prefer Dorian (darker); high Mixolydian can creep in
        val useDorian = level < 4 || rng.nextFloat() < 0.6f
        val (modeRoot, modeIntervals) = if (useDorian)
            Pair(50, DORIAN_INTERVALS)    // D3 Dorian
        else
            Pair(55, MIXOLYDIAN_INTERVALS) // G3 Mixolydian

        val scale = buildModeScale(modeRoot, modeIntervals) // frequencies for each scale degree

        // Tempo: level 1 = 60 BPM (slow, sad), accelerates gently
        val bpm = (55f + (level - 1) * 6f).coerceAtMost(110f)
        val beatMs = (60000f / bpm).toInt()    // one quarter-beat in ms
        val stepMs = beatMs / 2                // 8th-note grid step

        // Phrase/loop length: 4 bars of 8 steps = 32 steps
        val barsPerLoop = 4
        val stepsPerBar = 8
        val totalSteps  = barsPerLoop * stepsPerBar
        val totalMs     = totalSteps * stepMs
        val totalSamples = sampleRate * totalMs / 1000
        val master = ShortArray(totalSamples)

        // Build Markov transition matrix for this composition
        val markovMatrix = buildMelodyTransitions(scale.size, rng, destiny)

        // ---- LAYER 1: HARP (always present) ----
        // Generates a seeded melody using the Markov chain
        // Level 1: sparse, slow arpeggios. Higher levels: denser, more rhythmic.
        writeHarpLayer(master, scale, markovMatrix, rng, stepMs, totalSteps, level, sampleRate)

        // ---- LAYER 2: DRONE STRINGS (level 2+) ----
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
        }

        // ---- BONUS: Trumpeter ancillary ----
        if (hasTrumpeter) {
            writeTrumpeterLayer(master, scale, rng, stepMs, totalSteps, sampleRate)
        }

        return master
    }

    // ---- LAYER WRITERS ----

    private fun writeHarpLayer(
        master: ShortArray, scale: FloatArray,
        markov: Array<FloatArray>, rng: Random,
        stepMs: Int, totalSteps: Int, level: Int, sampleRate: Int
    ) {
        // Start at a middle-low degree for a melancholic feel
        var degree = 2 // F or B depending on mode (sad 3rd of mode)

        // Generate rhythmic phrase pattern for the whole loop
        val rhythm = generateRhythmicPhrase(stepMs, totalSteps, rng, level)

        var currentStep = 0
        for ((durationMs, isRest) in rhythm) {
            if (!isRest && currentStep < totalSteps) {
                val freq = scale[degree.coerceIn(0, scale.size - 1)]
                val velocity = 0.75f + rng.nextFloat() * 0.35f // natural variation

                // Arpeggiate: add organum tones (5th above, octave) with slight time offsets
                val staggerMs = 35 + rng.nextInt(25) // 35–60ms stagger between strings
                val noteMs = (durationMs * 0.92f).toInt() // slightly shorter than the step

                // Root note
                val startMs = currentStep * stepMs.toFloat()
                mix(master, harpNote(freq, noteMs, velocity, sampleRate),
                    sampleOffset(startMs, sampleRate), 1.0f)

                // Add a perfect 5th above (organum) at lower velocity, if not too high
                val fifthDegree = degree + 4  // approx a 5th up in modal scale
                if (fifthDegree < scale.size) {
                    val fifthFreq = scale[fifthDegree]
                    mix(master, harpNote(fifthFreq, noteMs, velocity * 0.6f, sampleRate),
                        sampleOffset(startMs + staggerMs, sampleRate), 0.9f)
                }

                // At higher levels add the octave for richness
                if (level >= 3) {
                    val octDegree = degree + 7 // approx octave in 15-note scale
                    if (octDegree < scale.size) {
                        val octFreq = scale[octDegree]
                        mix(master, harpNote(octFreq, noteMs, velocity * 0.4f, sampleRate),
                            sampleOffset(startMs + staggerMs * 2f, sampleRate), 0.8f)
                    }
                }

                degree = nextMarkovDegree(degree.coerceIn(0, markov.size - 1), markov, rng)
                    .coerceIn(0, scale.size - 1)
            }
            currentStep += durationMs / stepMs
        }

        // Always add a slow bass pedal point (drone low note) on the beat
        // This grounds the harmony in an authentic medieval way
        val bassFreq = scale[0] / 2f // one octave below root
        for (beat in 0 until (totalSteps / 4)) {
            val startMs = beat * 4 * stepMs.toFloat()
            val bassVelocity = 0.9f + rng.nextFloat() * 0.15f
            mix(master, harpNote(bassFreq, stepMs * 4, bassVelocity, sampleRate),
                sampleOffset(startMs, sampleRate), 0.7f)
        }
    }

    private fun writeDroneLayer(
        master: ShortArray, modeRootMidi: Int,
        totalMs: Int, rng: Random,
        destiny: MusicalDestiny, sampleRate: Int
    ) {
        // Continuous low drone: root and 5th (perfect organum)
        val rootFreq  = midiToFreq(modeRootMidi - 12) // one octave below
        val fifthFreq = midiToFreq(modeRootMidi - 12 + 7)

        val droneRoot  = droneNote(rootFreq,  totalMs, sampleRate, if (destiny == MusicalDestiny.ORCHESTRAL) 0.22f else 0.18f)
        val droneFifth = droneNote(fifthFreq, totalMs, sampleRate, 0.12f)

        mix(master, droneRoot,  0, 1f)
        mix(master, droneFifth, 0, 1f)
    }

    private fun writeRecorderLayer(
        master: ShortArray, scale: FloatArray,
        markov: Array<FloatArray>, rng: Random,
        stepMs: Int, totalSteps: Int, level: Int, sampleRate: Int
    ) {
        // Recorder plays a counter-melody one octave higher than the harp
        // It uses the same Markov matrix but starts from a different degree
        var degree = 5 // upper part of the scale for brightness

        val rhythm = generateRhythmicPhrase(stepMs, totalSteps, rng, level + 1)
        var currentStep = 0
        for ((durationMs, isRest) in rhythm) {
            if (!isRest && currentStep < totalSteps) {
                val scaleDeg = degree.coerceIn(0, scale.size - 1)
                val freq = scale[scaleDeg]
                val startMs = currentStep * stepMs.toFloat()
                val noteMs = (durationMs * 0.85f).toInt()
                val vel = 0.7f + rng.nextFloat() * 0.25f
                mix(master, recorderNote(freq, noteMs, sampleRate, vel),
                    sampleOffset(startMs, sampleRate), 0.85f)

                degree = nextMarkovDegree(degree.coerceIn(0, markov.size - 1), markov, rng)
                    .coerceIn(0, scale.size - 1)
            }
            currentStep += durationMs / stepMs
        }
    }

    private fun writeBodhranLayer(
        master: ShortArray, stepMs: Int, totalSteps: Int,
        rng: Random, destiny: MusicalDestiny, sampleRate: Int
    ) {
        // Bodhran: frame drum with duple/triple feel
        // FANFARE destiny → strong duple meter (1 . 3 . / 1 . 3 .)
        // ORCHESTRAL destiny → gentler triple lilt (1 . . 2 . . / …)
        val beatPattern: List<Pair<Int, Float>> = if (destiny == MusicalDestiny.FANFARE) {
            // Strong 2-beat pulses per bar of 8 steps: beat 0, 4
            listOf(0 to 1.2f, 2 to 0.5f, 4 to 1.0f, 6 to 0.5f)
        } else {
            // Lilting triple: beat 0, 3, 5 per bar (3+2+3 / 8)
            listOf(0 to 1.2f, 3 to 0.7f, 5 to 0.9f)
        }

        val stepsPerBar = 8
        val numBars = totalSteps / stepsPerBar
        val drumMs = (stepMs * 1.5f).toInt()

        for (bar in 0 until numBars) {
            for ((stepOffset, accent) in beatPattern) {
                val globalStep = bar * stepsPerBar + stepOffset
                if (globalStep >= totalSteps) continue
                // Occasional syncopation variety
                val actualAccent = if (rng.nextFloat() < 0.12f) accent * 0.4f else accent
                mix(master, bodhranBeat(drumMs, sampleRate, actualAccent),
                    sampleOffset((globalStep * stepMs).toFloat(), sampleRate), 1.0f)
            }
        }
    }

    private fun writeHornLayer(
        master: ShortArray, scale: FloatArray,
        markov: Array<FloatArray>, rng: Random,
        stepMs: Int, totalSteps: Int, destiny: MusicalDestiny, sampleRate: Int
    ) {
        // Horns play long held organum 5ths on structural beats
        // FANFARE: bright rising fanfare figures
        // ORCHESTRAL: sweeping sustained chords
        val stepsPerBar = 8
        val numBars = totalSteps / stepsPerBar

        for (bar in 0 until numBars) {
            // Choose a structurally important degree from the scale
            val rootDeg = when {
                destiny == MusicalDestiny.FANFARE && bar % 2 == 0 -> 7  // upper region
                destiny == MusicalDestiny.ORCHESTRAL              -> 4  // mid region
                else                                              -> 6
            }.coerceIn(0, scale.size - 1)

            val rootFreq = scale[rootDeg]
            val fifthDeg = (rootDeg + 4).coerceIn(0, scale.size - 1)
            val fifthFreq = scale[fifthDeg]

            val startMs = (bar * stepsPerBar * stepMs).toFloat()
            val holdMs = stepMs * if (destiny == MusicalDestiny.FANFARE) 4 else 6

            val rootVol  = if (destiny == MusicalDestiny.FANFARE) 1.0f else 0.75f
            val fifthVol = if (destiny == MusicalDestiny.FANFARE) 0.8f else 0.65f

            mix(master, hornNote(rootFreq, holdMs, sampleRate, rootVol),
                sampleOffset(startMs, sampleRate), 0.9f)
            mix(master, hornNote(fifthFreq, holdMs, sampleRate, fifthVol),
                sampleOffset(startMs + 15, sampleRate), 0.8f)
        }
    }

    private fun writePsalteryLayer(
        master: ShortArray, scale: FloatArray,
        markov: Array<FloatArray>, rng: Random,
        stepMs: Int, totalSteps: Int, sampleRate: Int
    ) {
        // Psaltery ornaments the melody with a gentle counter-line
        // Greensleeves-esque sweeping descent through the mode
        // Play every 2nd step, descending arch over 16 steps then repeat
        val arch = listOf(11, 10, 9, 8, 7, 8, 9, 10, 11, 12, 11, 10, 9, 8, 7, 6)
        for (i in 0 until totalSteps) {
            if (i % 2 == 0) {
                val deg = arch[i % arch.size].coerceIn(0, scale.size - 1)
                val freq = scale[deg]
                val startMs = (i * stepMs).toFloat()
                val noteMs = (stepMs * 1.8f).toInt()
                mix(master, psalterNote(freq, noteMs, sampleRate),
                    sampleOffset(startMs, sampleRate), 0.7f)
            }
        }
    }

    private fun writeTrumpeterLayer(
        master: ShortArray, scale: FloatArray,
        rng: Random, stepMs: Int, totalSteps: Int, sampleRate: Int
    ) {
        // Comedic slightly-detuned trumpeter ancillary blasts
        val blastSteps = listOf(1, 9, 17, 25).filter { it < totalSteps }
        for (step in blastSteps) {
            val deg = (3..7).random(rng).coerceIn(0, scale.size - 1)
            val freq = scale[deg] * (1f + (rng.nextFloat() - 0.5f) * 0.04f) // ±2% detune for comedy
            val startMs = (step * stepMs).toFloat()
            val blastMs = stepMs * 2
            mix(master, hornNote(freq, blastMs, sampleRate, 1.2f),
                sampleOffset(startMs, sampleRate), 1.3f)
        }
    }
}

// ---------------------------------------------------------------------------
// MEDIEVAL VOCALIZER (unchanged)
// ---------------------------------------------------------------------------

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