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

// ---------------------------------------------------------------------------
// Sound effects + TTS. The procedural music composer now lives in
// music\ProceduralMedievalComposer.kt (same package); the streaming player
// lives in MedievalHarpPlayer.kt at the repo root.
// ---------------------------------------------------------------------------

object MedievalAudioSynth {
    private const val SAMPLE_RATE = 22050
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

        private fun playFromAssetFolder(folder: String): Boolean {
        if (appContext == null) return false
        try {
            val am = appContext!!.assets
            val files = am.list(folder)
            if (files != null && files.isNotEmpty()) {
                val audioFiles = files.filter { it.endsWith(".wav") || it.endsWith(".ogg") || it.endsWith(".mp3") || it.endsWith(".mid") || it.endsWith(".midi") }
                if (audioFiles.isNotEmpty()) {
                    val randomFile = audioFiles.random()
                    val afd = am.openFd("$folder/$randomFile")
                    val player = android.media.MediaPlayer()
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    player.setOnCompletionListener { it.release() }
                    player.prepare()
                    player.start()
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    @Volatile
    var sfxEnabled = true

    /** Occasional wardog bark from the assets/dog folder. */
    fun playDogBark() {
        if (!sfxEnabled) return
        playFromAssetFolder("dog")
    }

    /** Occasional hag cackle from the assets/hag folder (empty folder plays nothing). */
    fun playHagCackle() {
        if (!sfxEnabled) return
        playFromAssetFolder("hag")
    }

    fun playSound(type: SoundType) {
        if (!sfxEnabled) return
        if (type == SoundType.DRUM_ROLL) {
            if (playFromAssetFolder("drums")) return
        }
        
        if (type == SoundType.VICTORY_FANFARE) {
            if (playFromAssetFolder("victory")) return
        }
        
        if (type == SoundType.OUCH) {
            if (playFromAssetFolder("pain")) return
        }

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

    private fun generateBuffer(type: SoundType): ShortArray =
        SfxGenerator.generate(type, SAMPLE_RATE, kotlin.random.Random(System.nanoTime()))
}


