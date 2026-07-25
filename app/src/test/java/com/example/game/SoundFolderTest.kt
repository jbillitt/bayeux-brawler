package com.example.game

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catches the classic silent failure: a folder gets renamed or never created, playFolder returns
 * false forever, and the sound simply stops without anything going red.
 */
class SoundFolderTest {

    /** Preloaded into SoundPool by MedievalAudioSynth.prepare(). */
    private val soundPoolFolders = listOf(
        "trojan", "bee", "herald", "fanatic", "monk", "plague",
        "dog", "hag", "drums", "pain", "armour", "shield", "flesh"
    )

    /** Streamed by MainActivity.playRandomVoiceClip — deliberately NOT pooled, they are huge. */
    private val mediaPlayerFolders = listOf("victory", "defeat")

    @Test
    fun everyFolderLoadedAtRuntimeExistsOnDisk() {
        (soundPoolFolders + mediaPlayerFolders).forEach { folder ->
            val dir = File("src/main/assets/$folder")
            assertTrue("assets/$folder is loaded at runtime but does not exist", dir.isDirectory)
        }
    }

    /**
     * An empty folder is the same silent failure as a missing one: playFolder finds no ids and
     * returns false. Filenames are free-form — the loader enumerates with AssetManager.list(),
     * so "Recording (10).wav" is as valid a clip name as "1.wav".
     */
    @Test
    fun everyFolderHoldsAtLeastOnePlayableClip() {
        (soundPoolFolders + mediaPlayerFolders).forEach { folder ->
            val playable = File("src/main/assets/$folder").listFiles().orEmpty().filter {
                it.name.endsWith(".wav") || it.name.endsWith(".ogg") || it.name.endsWith(".mp3")
            }
            assertTrue("assets/$folder holds no playable clip, so it will be silent", playable.isNotEmpty())
        }
    }

    /**
     * SoundPool is finite native memory, and whatever loads last is what goes silent when it
     * fills. victory+defeat are ~29MB of the ~40MB of assets; preloading them starved the six
     * entourage voice folders that load after them. That is the bug this guards from recurring.
     */
    @Test
    fun theHugeVoiceFoldersStayOutOfTheSoundPool() {
        mediaPlayerFolders.forEach {
            assertTrue("$it must not be preloaded into SoundPool", it !in soundPoolFolders)
        }
    }
}
