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

    private fun generateBuffer(type: SoundType): ShortArray =
        SfxGenerator.generate(type, SAMPLE_RATE, kotlin.random.Random(System.nanoTime()))
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
                        tts?.setLanguage(java.util.Locale.UK)
                    }
                    
                    val voices = tts?.voices ?: emptySet()
                    val oldspeakVoice = voices.firstOrNull { 
                        (it.locale.country == "GB" || it.name.contains("gb", true)) && 
                        (it.name.contains("male", true) || it.name.contains("rjs", true) || it.name.contains("gbd", true))
                    } ?: voices.firstOrNull { it.name.contains("male", true) }
                    
                    if (oldspeakVoice != null) {
                        tts?.voice = oldspeakVoice
                    }
                    tts?.setPitch(0.4f) // lowered pitch for a gruff, oldspeak male voice
                    tts?.setSpeechRate(0.85f)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speak(text: String) {
        if (!isInitialized) return
        try {
            // "Oldspeak" phonetic overrides for TTS engine
            val phoneticText = text.lowercase()
                .replace("væ", "vay")
                .replace("æ", "e")
                .replace("mihi", "mee-hee")
                .replace("mortis", "mortis")
                .replace("ouch", "owch")
            tts?.speak(phoneticText, TextToSpeech.QUEUE_FLUSH, null, "medieval_vocal_bark")
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
