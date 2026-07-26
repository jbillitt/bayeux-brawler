package com.example.game

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Renders the BRAWL ("fists only") music to app/build/music_preview/ as WAVs so the lead
 * instrument can be auditioned outside the game. Same seed, same drums, same riff in every
 * file — only the voice carrying the tune changes, so the files are directly comparable.
 */
class BrawlLeadPreviewTest {
    private val sampleRate = 44100

    /** name to (lead voice, forced harp stringing) */
    private val variants = listOf(
        Triple("a_shawm", Voice.SHAWM, null),          // the original lead, restored
        Triple("b_harp_gittern", Voice.HARP, 1f),      // harp strung/plucked like a guitar
        Triple("c_harp_current", Voice.HARP, 0f)       // the harp lead as it shipped yesterday
    )

    @Test
    fun `brawl lead variants render as audible wavs`() {
        val outDir = File("build/music_preview").apply { mkdirs() }
        try {
            for (seed in listOf(7L, 4242L)) {
                for ((name, lead, timbre) in variants) {
                    brawlLeadOverride = lead
                    ProceduralMedievalComposer.harpTimbreOverride = timbre
                    val pcm = ProceduralMedievalComposer.compose(
                        seed, level = 6, hasTrumpeter = true, sampleRate = sampleRate, brawl = true
                    )
                    assertTrue("$name/$seed is silent", pcm.any { Math.abs(it.toInt()) > 1000 })
                    writeWav(File(outDir, "brawl_${name}_seed$seed.wav"), pcm)
                }
            }
        } finally {
            brawlLeadOverride = null
            ProceduralMedievalComposer.harpTimbreOverride = null
        }
    }

    /** 16-bit interleaved stereo, which is what compose() hands back. */
    private fun writeWav(file: File, pcm: ShortArray) {
        val dataLen = pcm.size * 2
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(2)
        header.putInt(sampleRate)
        header.putInt(sampleRate * 4)
        header.putShort(4)
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataLen)
        val body = java.nio.ByteBuffer.allocate(dataLen).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { body.putShort(it) }
        file.outputStream().use {
            it.write(header.array())
            it.write(body.array())
        }
    }
}
