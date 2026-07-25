package com.example.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
// Sound effects + TTS. The procedural music composer now lives in
// music\ProceduralMedievalComposer.kt (same package); the streaming player
// lives in MedievalHarpPlayer.kt at the repo root.
//
// Playback core: every effect used to synthesize its PCM from scratch AND
// build a brand-new AudioTrack per play (a heavyweight AudioFlinger round
// trip), with asset sounds doing a synchronous MediaPlayer.prepare() on the
// calling game thread. Combat spam made the audio server the frame budget's
// biggest tenant — muting sound visibly sped the game up. Now: all variants
// are pre-rendered once at init into cached WAVs and loaded into ONE
// SoundPool; playSound() is a single native mixer call.
// ---------------------------------------------------------------------------

object MedievalAudioSynth {
    private const val SAMPLE_RATE = 22050
    /** Pre-rendered takes per effect, so repeated hits don't sound machine-identical. */
    private const val VARIANTS = 3
    /** Same effect re-triggered inside this window is dropped — spam guard, inaudible loss. */
    private const val MIN_GAP_MS = 70L
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var appContext: Context? = null

    private var soundPool: SoundPool? = null
    private val synthIds = ConcurrentHashMap<SoundType, List<Int>>()
    private val folderPoolIds = ConcurrentHashMap<String, List<Int>>()
    /** Folders whose only audio is MIDI — SoundPool can't decode it, keep the MediaPlayer path. */
    private val midiOnlyFolders = ConcurrentHashMap.newKeySet<String>()
    private val lastPlayedMs = ConcurrentHashMap<SoundType, Long>()

    @Volatile
    var sfxEnabled = true

    fun init(context: Context) {
        appContext = context.applicationContext
        if (soundPool != null) return
        val sp = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        soundPool = sp
        scope.launch { prepare(sp) }
    }

    private fun prepare(sp: SoundPool) {
        val ctx = appContext ?: return
        try {
            for (type in SoundType.values()) {
                val ids = mutableListOf<Int>()
                repeat(VARIANTS) { v ->
                    val pcm = SfxGenerator.generate(type, SAMPLE_RATE, kotlin.random.Random(type.ordinal * 131L + v))
                    val f = File(ctx.cacheDir, "sfx_${type.name.lowercase()}_$v.wav")
                    writeWav(f, pcm)
                    ids += sp.load(f.absolutePath, 1)
                }
                synthIds[type] = ids
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Voices first: SoundPool decodes into a finite native heap, and whatever loads last is
        // what goes silent when it fills. "victory"/"defeat" are deliberately absent — they are
        // long one-shots that MainActivity.playRandomVoiceClip streams via MediaPlayer, and
        // preloading victory alone cost 18MB of the pool that the entourage voices needed.
        for (folder in listOf(
            "trojan", "bee", "herald", "fanatic", "monk", "plague",
            "dog", "hag", "drums", "pain", "armour", "shield", "flesh"
        )) {
            // Per-folder, so one unreadable asset cannot silence every folder after it.
            try {
                val files = ctx.assets.list(folder)?.toList().orEmpty()
                val poolable = files.filter {
                    it.endsWith(".wav") || it.endsWith(".ogg") || it.endsWith(".mp3")
                }
                if (poolable.isNotEmpty()) {
                    folderPoolIds[folder] = poolable.map { name ->
                        sp.load(ctx.assets.openFd("$folder/$name"), 1)
                    }
                } else if (files.any { it.endsWith(".mid") || it.endsWith(".midi") }) {
                    midiOnlyFolders.add(folder)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Minimal 16-bit mono PCM WAV writer for the pre-rendered effect cache. */
    private fun writeWav(file: File, pcm: ShortArray) {
        val dataLen = pcm.size * 2
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)               // PCM chunk size
        header.putShort(1)              // PCM format
        header.putShort(1)              // mono
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2)  // byte rate
        header.putShort(2)              // block align
        header.putShort(16)             // bits per sample
        header.put("data".toByteArray())
        header.putInt(dataLen)
        val body = java.nio.ByteBuffer.allocate(dataLen).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { body.putShort(it) }
        file.outputStream().use {
            it.write(header.array())
            it.write(body.array())
        }
    }

    /** Legacy path, kept only for MIDI-only folders — always off the game thread. */
    private fun playFolderViaMediaPlayer(folder: String) {
        val ctx = appContext ?: return
        try {
            val files = ctx.assets.list(folder)?.filter {
                it.endsWith(".mid") || it.endsWith(".midi")
            }.orEmpty()
            if (files.isEmpty()) return
            val afd = ctx.assets.openFd("$folder/${files.random()}")
            val player = android.media.MediaPlayer()
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            player.setOnCompletionListener { it.release() }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playFolder(folder: String): Boolean {
        folderPoolIds[folder]?.let { ids ->
            soundPool?.play(ids.random(), 1f, 1f, 1, 0, 1f)
            return true
        }
        if (folder in midiOnlyFolders) {
            scope.launch { playFolderViaMediaPlayer(folder) }
            return true
        }
        return false
    }

    /** Occasional wardog bark from the assets/dog folder. */
    fun playDogBark() {
        if (sfxEnabled) playFolder("dog")
    }

    /** Occasional hag cackle from the assets/hag folder (empty folder plays nothing). */
    fun playHagCackle() {
        if (sfxEnabled) playFolder("hag")
    }

    /** The belly of the great horse splitting open — one cry per man tumbling out of it. */
    fun playTrojanBurst(voices: Int = 3) {
        if (!sfxEnabled) return
        scope.launch {
            repeat(voices) {
                playFolder("trojan")
                kotlinx.coroutines.delay(200L)
            }
        }
    }

    /** Humble Bede's hive bursting among the enemy. */
    fun playBeeSwarm() { if (sfxEnabled) playFolder("bee") }

    /** Sir Boast-a-lot announcing you, at length. */
    fun playHeraldBoast() { if (sfxEnabled) playFolder("herald") }

    /** Mad Boris, charging. */
    fun playFanaticScream() { if (sfxEnabled) playFolder("fanatic") }

    /** Brother Tuck at his devotions. */
    fun playMonkChant() { if (sfxEnabled) playFolder("monk") }

    /** Wretched Aldwin's cough, which is also his weapon. */
    fun playPlagueCough() { if (sfxEnabled) playFolder("plague") }

    fun playSound(type: SoundType) {
        if (!sfxEnabled) return
        val now = SystemClock.uptimeMillis()
        if (now - (lastPlayedMs[type] ?: 0L) < MIN_GAP_MS) return
        lastPlayedMs[type] = now

        when (type) {
            SoundType.DRUM_ROLL -> if (playFolder("drums")) return
            SoundType.VICTORY_FANFARE -> if (playFolder("victory")) return
            SoundType.OUCH -> if (playFolder("pain")) return
            // Player-recorded impact takes; the synth fallback below covers empty folders
            SoundType.ARMOUR_HIT -> if (playFolder("armour")) return
            SoundType.SHIELD_BLOCK -> if (playFolder("shield")) return
            SoundType.FLESH -> if (playFolder("flesh")) return
            else -> {}
        }
        val ids = synthIds[type] ?: return
        soundPool?.play(ids.random(), 0.9f, 0.9f, 1, 0, 1f)
    }
}
