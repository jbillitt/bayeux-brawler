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
    /** Clip length per pooled id, parallel to [folderPoolIds] — how long a voice stays busy. */
    private val folderClipMs = ConcurrentHashMap<String, List<Long>>()
    /** Folders whose only audio is MIDI — SoundPool can't decode it, keep the MediaPlayer path. */
    private val midiOnlyFolders = ConcurrentHashMap.newKeySet<String>()
    private val lastPlayedMs = ConcurrentHashMap<SoundType, Long>()
    /**
     * When each *voice* falls silent again. A voice is one throat: one beekeeper, one monk, one
     * man tumbling out of the horse. A throat may not talk over itself, so a repeat inside the
     * clip's own length is dropped — that is the "spam" the entourage used to produce. Distinct
     * emitters pass distinct keys and are never gated against each other, because three monks
     * chanting at once is three monks, not a bug.
     */
    private val voiceBusyUntilMs = ConcurrentHashMap<String, Long>()
    /** Fallback when a clip's real length could not be read. */
    private const val ASSUMED_CLIP_MS = 1500L

    @Volatile
    var sfxEnabled = true

    fun init(context: Context) {
        appContext = context.applicationContext
        if (soundPool != null) return
        val sp = SoundPool.Builder()
            // 8 was too tight: SoundPool evicts the oldest stream once the cap is hit, so the
            // three men bursting out of the Trojan Horse cut each other off the moment combat
            // was also making noise. Voices below play at a higher priority than the synth
            // clangs for the same reason — a shout must not be evicted by a sword.
            .setMaxStreams(24)
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
            "dog", "hag", "drums", "pain", "armour", "shield", "flesh",
            "bomb", "goose"
        )) {
            // Per-folder, so one unreadable asset cannot silence every folder after it.
            try {
                val files = ctx.assets.list(folder)?.toList().orEmpty()
                val poolable = files.filter {
                    it.endsWith(".wav") || it.endsWith(".ogg") || it.endsWith(".mp3")
                }
                if (poolable.isNotEmpty()) {
                    folderPoolIds[folder] = poolable.map { name ->
                        sp.load(ctx.assets.openFd("$folder/$name"), 2)
                    }
                    folderClipMs[folder] = poolable.map { name -> clipLengthMs(ctx, folder, name) }
                } else if (files.any { it.endsWith(".mid") || it.endsWith(".midi") }) {
                    midiOnlyFolders.add(folder)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** How long an asset clip runs, so a voice knows when its own throat is free again. */
    private fun clipLengthMs(ctx: Context, folder: String, name: String): Long = try {
        val r = android.media.MediaMetadataRetriever()
        ctx.assets.openFd("$folder/$name").use { fd ->
            r.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        }
        val ms = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: ASSUMED_CLIP_MS
        r.release()
        ms
    } catch (e: Exception) {
        ASSUMED_CLIP_MS
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

    /**
     * @param voice the throat this sound comes out of. Non-null gates the clip against ITSELF for
     *   its own length — one man cannot shout over his own shout. Pass a distinct key per emitting
     *   entity ("monk#0", "monk#1", a fighter id) and they overlap each other freely, which is
     *   what a crowd sounds like. Null means no gate at all, for impact folders (armour, flesh,
     *   shield) where every blow on the field is a different, legitimately simultaneous source.
     * @return true if this folder handled the request — including when the voice gate suppressed
     *   it, so a suppressed voice never falls through to a synthesised substitute.
     */
    private fun playFolder(folder: String, voice: String? = null): Boolean {
        folderPoolIds[folder]?.let { ids ->
            if (voice != null) {
                val now = SystemClock.uptimeMillis()
                if (now < (voiceBusyUntilMs[voice] ?: 0L)) return true
                val i = ids.indices.random()
                voiceBusyUntilMs[voice] = now + (folderClipMs[folder]?.getOrNull(i) ?: ASSUMED_CLIP_MS)
                soundPool?.play(ids[i], 1f, 1f, 2, 0, 1f)
            } else {
                soundPool?.play(ids.random(), 1f, 1f, 2, 0, 1f)
            }
            return true
        }
        if (folder in midiOnlyFolders) {
            scope.launch { playFolderViaMediaPlayer(folder) }
            return true
        }
        return false
    }

    /** Occasional wardog bark from the assets/dog folder. One bark per dog, not per pack. */
    fun playDogBark(voice: String = "dog") {
        if (sfxEnabled) playFolder("dog", voice)
    }

    /** Occasional hag cackle from the assets/hag folder (empty folder plays nothing). */
    fun playHagCackle(voice: String = "hag") {
        if (sfxEnabled) playFolder("hag", voice)
    }

    /** The belly of the great horse splitting open — one cry per man tumbling out of it. */
    fun playTrojanBurst(voices: Int = 3) {
        if (!sfxEnabled) return
        scope.launch {
            repeat(voices) { i ->
                // A voice key per man: these are meant to pile up on each other.
                playFolder("trojan", "trojan#$i")
                kotlinx.coroutines.delay(200L)
            }
        }
    }

    /** Humble Bede's hive bursting among the enemy. */
    fun playBeeSwarm(voice: String = "bee") { if (sfxEnabled) playFolder("bee", voice) }

    /**
     * A pot of black powder going off. No voice key: every burst on the field is its own source
     * and they are meant to overlap.
     */
    fun playBombBurst() { if (sfxEnabled) playFolder("bomb") }

    /** The goose lashed to a weapon, getting hold of somebody. On-hit only. */
    fun playGooseBite() { if (sfxEnabled) playFolder("goose") }

    /** Sir Boast-a-lot announcing you, at length. */
    fun playHeraldBoast() { if (sfxEnabled) playFolder("herald", "herald") }

    /** Mad Boris, charging. */
    fun playFanaticScream(voice: String = "fanatic") { if (sfxEnabled) playFolder("fanatic", voice) }

    /** Brother Tuck at his devotions. */
    fun playMonkChant(voice: String = "monk") { if (sfxEnabled) playFolder("monk", voice) }

    /** Wretched Aldwin's cough, which is also his weapon. */
    fun playPlagueCough(voice: String = "plague") { if (sfxEnabled) playFolder("plague", voice) }

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
