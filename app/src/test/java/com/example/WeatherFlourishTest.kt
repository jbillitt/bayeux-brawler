package com.example

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.example.game.DivineWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherFlourishTest {

    private val canvasSizes = listOf(
        Size(2400f, 1080f),
        Size(1280f, 800f)
    )

    @Test
    fun `phone and tablet inner fields exclude border and artwork bands`() {
        canvasSizes.forEach { size ->
            val field = innerFieldRect(size, outerBorderPx = 6f)

            assertEquals(6f, field.left)
            assertEquals(size.width - 6f, field.right)
            assertEquals(TAPESTRY_BORDER_BAND_PX + 6f, field.top)
            assertEquals(size.height - TAPESTRY_BORDER_BAND_PX - 6f, field.bottom)
        }
    }

    @Test
    fun `every weather washes exactly the clipped inner field at every sweep point`() {
        canvasSizes.forEach { size ->
            val field = innerFieldRect(size, outerBorderPx = 6f)
            DivineWeather.values().forEach { weather ->
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
                    val geometry = weatherFlourishGeometry(weather, field, progress)

                    assertEquals(field, geometry.fieldRect)
                    assertEquals(field, geometry.washRect)
                    assertTrue(geometry.washRect.isInside(size))
                }
            }
        }
    }

    @Test
    fun `lightning scales strike and ground geometry inside each field`() {
        canvasSizes.forEach { size ->
            val field = innerFieldRect(size, outerBorderPx = 6f)
            val geometry = weatherFlourishGeometry(
                DivineWeather.LIGHTNING,
                field,
                progress = 0.5f
            ) as LightningFlourishGeometry

            assertEquals(field.top, geometry.skyY)
            assertTrue(geometry.groundY in field.top..field.bottom)
            assertTrue(geometry.strikeXs.all { it in field.left..field.right })
            assertTrue(geometry.jaggedXRadius <= field.width * 0.03f)
        }
    }

    @Test
    fun `flood starts fully before field and finishes fully beyond it`() {
        canvasSizes.forEach { size ->
            val field = innerFieldRect(size, outerBorderPx = 6f)
            val start = weatherFlourishGeometry(
                DivineWeather.FLOOD,
                field,
                progress = 0f
            ) as FloodFlourishGeometry
            val end = weatherFlourishGeometry(
                DivineWeather.FLOOD,
                field,
                progress = 1f
            ) as FloodFlourishGeometry

            assertTrue(start.edgeX + start.frontExtent < field.left)
            assertTrue(end.edgeX - end.backExtent > field.right)
            assertEquals(start.sweepStartEdgeX, start.edgeX)
            assertEquals(end.sweepEndEdgeX, end.edgeX)
        }
    }

    @Test
    fun `hail and frost drawing zones stay within phone and tablet fields`() {
        canvasSizes.forEach { size ->
            val field = innerFieldRect(size, outerBorderPx = 6f)
            val hail = weatherFlourishGeometry(
                DivineWeather.HAIL,
                field,
                progress = 0.5f
            ) as HailFlourishGeometry
            val frost = weatherFlourishGeometry(
                DivineWeather.FROST,
                field,
                progress = 0.5f
            ) as FrostFlourishGeometry

            assertTrue(hail.dropRect.isInside(field))
            assertTrue(hail.groundY in field.top..field.bottom)
            assertTrue(hail.impactBottomY in hail.groundY..field.bottom)
            assertTrue(frost.bandTopY in field.top..field.bottom)
            assertTrue(frost.crystalRect.isInside(field))
        }
    }

    @Test
    fun `weather geometry follows a translated inner field instead of raw canvas origin`() {
        val original = Rect(6f, 46f, 1274f, 754f)
        val translated = original.translate(37f, 23f)

        DivineWeather.values().forEach { weather ->
            val first = weatherFlourishGeometry(weather, original, progress = 0.4f)
            val second = weatherFlourishGeometry(weather, translated, progress = 0.4f)

            assertEquals(translated, second.fieldRect)
            when {
                first is LightningFlourishGeometry && second is LightningFlourishGeometry -> {
                    assertFloatEquals(first.skyY + 23f, second.skyY)
                    assertFloatEquals(first.groundY + 23f, second.groundY)
                    first.strikeXs.zip(second.strikeXs).forEach { (firstX, secondX) ->
                        assertFloatEquals(firstX + 37f, secondX)
                    }
                }
                first is FloodFlourishGeometry && second is FloodFlourishGeometry -> {
                    assertFloatEquals(first.edgeX + 37f, second.edgeX)
                    assertFloatEquals(first.crestY + 23f, second.crestY)
                }
                first is HailFlourishGeometry && second is HailFlourishGeometry ->
                    assertRectEquals(first.dropRect.translate(37f, 23f), second.dropRect)
                first is FrostFlourishGeometry && second is FrostFlourishGeometry ->
                    assertRectEquals(first.crystalRect.translate(37f, 23f), second.crystalRect)
                first is FrogsFlourishGeometry && second is FrogsFlourishGeometry ->
                    assertRectEquals(first.dropRect.translate(37f, 23f), second.dropRect)
                else -> throw AssertionError("Mismatched geometry types for $weather")
            }
        }
    }

    private fun Rect.isInside(container: Rect): Boolean =
        left >= container.left &&
            top >= container.top &&
            right <= container.right &&
            bottom <= container.bottom

    private fun Rect.isInside(container: Size): Boolean =
        isInside(Rect(0f, 0f, container.width, container.height))

    private fun Rect.translate(dx: Float, dy: Float): Rect =
        Rect(left + dx, top + dy, right + dx, bottom + dy)

    private fun assertRectEquals(expected: Rect, actual: Rect) {
        assertFloatEquals(expected.left, actual.left)
        assertFloatEquals(expected.top, actual.top)
        assertFloatEquals(expected.right, actual.right)
        assertFloatEquals(expected.bottom, actual.bottom)
    }

    private fun assertFloatEquals(expected: Float, actual: Float) =
        assertEquals(expected.toDouble(), actual.toDouble(), 0.001)
}
