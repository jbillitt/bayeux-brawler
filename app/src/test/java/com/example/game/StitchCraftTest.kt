package com.example.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidPath
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

/**
 * The hand-worked line must be *hand-worked*, not *animated*. Every deviation has to be identical
 * every time the same shape is drawn, wherever it is on screen — otherwise the outlines crawl at
 * 60fps and the whole tapestry boils. That is a property of the maths, not of how it looks, so it
 * is checked here rather than by staring at a screenshot.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
// NATIVE graphics is required, not optional: Robolectric's legacy shadow of PathMeasure returns
// zeros for every getPosTan, so without this the test compares (0,0) against (0,0) and passes
// while proving nothing at all.
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(sdk = [34])
class StitchJitterTest {

    private fun blade(x: Float, y: Float) = androidx.compose.ui.graphics.Path().apply {
        moveTo(x, y)
        lineTo(x + 63f, y + 4f)
        lineTo(x + 58f, y + 41f)
        lineTo(x - 3f, y + 37f)
        close()
    }

    /** Walks the finished outline so two paths can be compared point for point. */
    private fun samples(path: androidx.compose.ui.graphics.Path): List<Offset> {
        val measure = android.graphics.PathMeasure(path.asAndroidPath(), false)
        val out = mutableListOf<Offset>()
        val point = FloatArray(2)
        do {
            var d = 0f
            while (d <= measure.length) {
                measure.getPosTan(d, point, null)
                out += Offset(point[0], point[1])
                d += 2f
            }
        } while (measure.nextContour())
        return out
    }

    @Test
    fun `the same shape jitters identically every time it is drawn`() {
        val first = samples(jitteredPath(blade(0f, 0f)))
        val again = samples(jitteredPath(blade(0f, 0f)))
        assertEquals("a redraw must not move a single stitch", first, again)
        assertTrue("the outline must actually have been sampled", first.size > 10)
    }

    /**
     * The one that matters. A fighter walking across the field hands us the same shape at a new
     * x every frame; if the deviations were keyed off absolute position, his outline would writhe
     * as he moved. Translating the shape must translate the jitter with it, unchanged.
     */
    @Test
    fun `moving a shape across the field does not change its stitching`() {
        val atOrigin = samples(jitteredPath(blade(0f, 0f)))
        val movedBack = samples(jitteredPath(blade(250f, 90f)))
            .map { Offset(it.x - 250f, it.y - 90f) }
        assertEquals("same number of stitches", atOrigin.size, movedBack.size)
        atOrigin.zip(movedBack).forEachIndexed { i, (a, b) ->
            assertEquals("stitch $i drifted in x", a.x, b.x, 0.01f)
            assertEquals("stitch $i drifted in y", a.y, b.y, 0.01f)
        }
    }

    /** The deviation is a real one — this proves the test above is not passing on a no-op. */
    @Test
    fun `the jitter actually displaces the outline`() {
        val straight = samples(blade(0f, 0f))
        val worked = samples(jitteredPath(blade(0f, 0f)))
        val drift = straight.zip(worked).maxOf { (a, b) ->
            kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y)
        }
        assertTrue("outline was not displaced at all: $drift", drift > 0.1f)
    }
}
