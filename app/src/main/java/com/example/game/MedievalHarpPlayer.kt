package com.example.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Streaming stereo music player. Renders are event-aligned across levels
 * (same seed = same tune), so level changes crossfade in-phase over 250 ms
 * instead of cutting. Handles audio focus and lifecycle pause/resume.
 */
object MedievalHarpPlayer {
    private const val SAMPLE_RATE = 44100
    private const val CHUNK_FRAMES = 4096
    private const val FADE_FRAMES = SAMPLE_RATE / 4   // 250 ms

    var isPlaying = false
        private set
    var gameSeed: Long = System.currentTimeMillis()
        private set

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var track: AudioTrack? = null
    private var feeder: Thread? = null
    @Volatile private var generation = 0
    @Volatile private var current: ShortArray? = null
    @Volatile private var pending: ShortArray? = null
    @Volatile private var fadeStartFrame = 0L
    @Volatile private var paused = false
    private var playheadFrames = 0L
    private var currentLevel = -1
    private var currentTrumpeter = false
    private var currentMoods: List<String> = emptyList()
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_GAIN -> resume()
        }
    }

    /** Call once from MainActivity.onCreate for audio-focus support. Safe to skip (no focus handling then). */
    fun init(context: Context) {
        audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    fun newGame(seed: Long = System.currentTimeMillis()) {
        gameSeed = seed
        currentLevel = -1
    }

    fun startMusic(level: Int = 1, hasTrumpeter: Boolean = false, moods: List<String> = emptyList()) {
        if (isPlaying && currentLevel == level && currentTrumpeter == hasTrumpeter && currentMoods == moods) return
        currentLevel = level; currentTrumpeter = hasTrumpeter; currentMoods = moods
        val myGen = ++generation
        val wasPlaying = isPlaying
        isPlaying = true
        scope.launch {
            try {
                val buf = ProceduralMedievalComposer.compose(gameSeed, level, hasTrumpeter, SAMPLE_RATE, moods)
                synchronized(this@MedievalHarpPlayer) {
                    if (generation != myGen) return@launch
                    if (wasPlaying && current != null && feeder?.isAlive == true) {
                        pending = buf                       // in-phase crossfade in the feeder
                        fadeStartFrame = playheadFrames
                    } else {
                        current = buf; pending = null; playheadFrames = 0L
                        begin()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace(); isPlaying = false
            }
        }
    }

    private fun begin() {
        requestFocus()
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, CHUNK_FRAMES * 4 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        t.play()
        track = t
        val myGen = generation
        feeder = Thread({ feederLoop(myGen) }, "MedievalHarpFeeder").also { it.isDaemon = true; it.start() }
    }

    private fun feederLoop(myGen: Int) {
        val out = ShortArray(CHUNK_FRAMES * 2)
        while (isPlaying && generation == myGen) {
            if (paused) { try { Thread.sleep(50) } catch (_: InterruptedException) {}; continue }
            val cur = current ?: break
            val t = track ?: break
            val loopFrames = cur.size / 2
            val pend = pending
            val pendFrames = pend?.let { it.size / 2 } ?: 0
            for (f in 0 until CHUNK_FRAMES) {
                val abs = playheadFrames + f
                val idx = (abs % loopFrames).toInt() * 2
                var l = cur[idx].toFloat(); var r = cur[idx + 1].toFloat()
                if (pend != null && pendFrames > 0) {
                    val p = (abs % pendFrames).toInt() * 2
                    val prog = ((abs - fadeStartFrame).toFloat() / FADE_FRAMES).coerceIn(0f, 1f)
                    l = l * (1f - prog) + pend[p] * prog
                    r = r * (1f - prog) + pend[p + 1] * prog
                }
                out[f * 2] = l.toInt().coerceIn(-32768, 32767).toShort()
                out[f * 2 + 1] = r.toInt().coerceIn(-32768, 32767).toShort()
            }
            if (pend != null && playheadFrames + CHUNK_FRAMES - fadeStartFrame >= FADE_FRAMES) {
                current = pend; pending = null
            }
            t.write(out, 0, out.size)   // blocking write paces the loop
            playheadFrames += CHUNK_FRAMES
        }
    }

    fun pause() {
        paused = true
        try { track?.pause() } catch (e: Exception) { e.printStackTrace() }
    }

    fun resume() {
        if (!isPlaying) return
        paused = false
        try { track?.play() } catch (e: Exception) { e.printStackTrace() }
    }

    fun stopMusic() {
        if (!isPlaying) return
        isPlaying = false
        generation++
        try {
            feeder?.join(500)
            track?.let { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop(); it.release() }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            track = null; feeder = null; current = null; pending = null; paused = false
            abandonFocus()
        }
    }

    private fun requestFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }; focusRequest = null
        } else {
            @Suppress("DEPRECATION") am.abandonAudioFocus(focusListener)
        }
    }
}
