package com.example.game

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catches the classic silent failure: a folder gets renamed or never created, playFolder returns
 * false forever, and the sound simply stops without anything going red.
 */
class SoundFolderTest {

    private val expected = listOf(
        "dog", "hag", "drums", "victory", "pain", "armour", "shield", "flesh",
        "trojan", "bee", "herald", "fanatic", "monk", "plague"
    )

    @Test
    fun everyFolderTheSynthLoadsExistsOnDisk() {
        expected.forEach { folder ->
            val dir = File("src/main/assets/$folder")
            assertTrue("assets/$folder is loaded by SoundSynth but does not exist", dir.isDirectory)
        }
    }
}
