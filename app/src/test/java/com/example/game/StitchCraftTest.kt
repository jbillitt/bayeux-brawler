package com.example.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StitchCraftTest {

    @Test
    fun `stitch geometry exactly matches the original formulas`() {
        listOf(
            Rect(0f, 0f, 5f, 5f),
            Rect(-12f, 4f, 188f, 18f),
            Rect(3f, -20f, 20f, 180f),
            Rect(10f, 15f, 1210f, 1015f)
        ).forEach { bounds ->
            assertEquals(oracleGeometry(bounds), stitchSegments(bounds))
        }
    }

    @Test
    fun `stitch density caps hold for representative bounds`() {
        listOf(
            Rect(0f, 0f, 20f, 5f),
            Rect(0f, 0f, 500f, 20f),
            Rect(0f, 0f, 20f, 500f),
            Rect(0f, 0f, 5000f, 5000f)
        ).forEach { bounds ->
            val geometry = stitchSegments(bounds)
            val rowStep = max(3.5f, bounds.height / 40f)
            val segmentLen = max(8f, bounds.width / 30f)
            val rowCount = geometry.horizontal.count { it.start.x == bounds.left - 5f }
            val segmentsPerRow = geometry.horizontal.size / rowCount

            assertTrue(rowCount <= ceil(bounds.height / 3.5f).toInt())
            assertTrue(rowCount <= 41)
            assertTrue(segmentsPerRow <= ceil(bounds.width / segmentLen).toInt() + 1)
        }
    }

    @Test
    fun `path builder visits every segment exactly once`() {
        val segments = listOf(
            StitchSegment(Offset(1f, 2f), Offset(3f, 4f)),
            StitchSegment(Offset(-5f, 6f), Offset(7f, -8f))
        )
        val visits = mutableListOf<Pair<String, Offset>>()

        appendStitchSegments(
            segments = segments,
            moveTo = { x, y -> visits += "move" to Offset(x, y) },
            lineTo = { x, y -> visits += "line" to Offset(x, y) }
        )

        assertEquals(
            listOf(
                "move" to segments[0].start,
                "line" to segments[0].end,
                "move" to segments[1].start,
                "line" to segments[1].end
            ),
            visits
        )
    }

    private fun oracleGeometry(bounds: Rect): StitchGeometry {
        val horizontal = mutableListOf<StitchSegment>()
        val anchors = mutableListOf<StitchSegment>()
        val rowStep = max(3.5f, bounds.height / 40f)
        val segmentLen = max(8f, bounds.width / 30f)
        var row = 0
        var y = bounds.top
        while (y < bounds.bottom) {
            var currX = bounds.left - 5f
            val endX = bounds.right + 5f
            var seg = 0
            var prevJitter = ((row * 7 + 3) % 5) * 0.3f - 0.6f
            while (currX < endX) {
                val nextX = currX + segmentLen
                val jitter = ((row * 31 + seg * 17) % 7) * 0.25f - 0.75f
                horizontal += StitchSegment(
                    Offset(currX, y + prevJitter),
                    Offset(nextX, y + jitter)
                )
                prevJitter = jitter
                currX = nextX
                seg++
            }

            var vx = bounds.left + ((row * 13) % 7)
            val vStep = segmentLen * 2.5f
            while (vx < bounds.right) {
                anchors += StitchSegment(Offset(vx, y - 2f), Offset(vx, y + 2f))
                vx += vStep
            }
            y += rowStep
            row++
        }
        return StitchGeometry(horizontal, anchors)
    }
}
