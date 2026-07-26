package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.drawWeatherFlourish
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The divine weather flourishes, drawn at landscape size so it can be seen whether each one
 * actually fills the field or huddles in one part of it.
 *
 * The reported symptoms were that a flourish covers only part of the screen and appears pinned to
 * a spot rather than to the battle. Both are visual questions, so they get looked at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w850dp-h393dp-land-xhdpi", sdk = [34])
class WeatherFlourishScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Each weather at three points through its life, stacked, over a field-coloured ground. */
    @Test
    fun everyWeatherAcrossItsLife() {
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(Color(0xFFC9B98F))) {
                DivineWeather.values().forEach { weather ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                            .background(Color(0xFFD8C9A3))
                    ) {
                        // Vertical guides at each third: anything that fails to reach the outer
                        // thirds is not washing the field, whatever its comment claims.
                        listOf(0.333f, 0.666f).forEach { f ->
                            drawRect(
                                color = Color(0x33000000),
                                topLeft = androidx.compose.ui.geometry.Offset(size.width * f, 0f),
                                size = androidx.compose.ui.geometry.Size(2f, size.height)
                            )
                        }
                        drawWeatherFlourish(this, weather, 0.35f)
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/landscape_weather_all.png")
    }

    /** One weather, full height, so its coverage across the whole field is unambiguous. */
    @Test
    fun lightningFillsTheField() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFD8C9A3))) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    listOf(0.333f, 0.666f).forEach { f ->
                        drawRect(
                            color = Color(0x33000000),
                            topLeft = androidx.compose.ui.geometry.Offset(size.width * f, 0f),
                            size = androidx.compose.ui.geometry.Size(2f, size.height)
                        )
                    }
                    drawWeatherFlourish(this, DivineWeather.LIGHTNING, 0.3f)
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/landscape_weather_lightning.png")
    }
}
