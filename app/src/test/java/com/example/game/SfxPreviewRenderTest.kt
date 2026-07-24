package com.example.game

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Renders the synthesized flesh-thud takes to build/sfx_preview/ as WAVs, so they can be
 * auditioned outside the game. Also the one runnable check that the generator produces
 * real, non-silent, non-clipped audio.
 */
class SfxPreviewRenderTest {
    private val sampleRate = 22050

    @Test
    fun `flesh thud variants render as audible non-clipped wavs`() {
        val outDir = File("build/sfx_preview").apply { mkdirs() }
        for (v in 0 until 6) {
            val rng = Random(SoundType.FLESH.ordinal * 131L + v)
            val pitchMul = Math.pow(2.0, (rng.nextDouble() * 4.0 - 2.0) / 12.0)
            val decayMul = 0.85 + rng.nextDouble() * 0.35
            val pcm = SfxGenerator.flesh(sampleRate, rng, pitchMul, decayMul)
            assertTrue("variant $v is silent", pcm.any { Math.abs(it.toInt()) > 1000 })
            assertTrue("variant $v hard-clips", pcm.none { it.toInt() == 32767 || it.toInt() == -32768 })
            writeWav(File(outDir, "flesh_thud_$v.wav"), pcm)
        }
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val dataLen = pcm.size * 2
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2)
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
