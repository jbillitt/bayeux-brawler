package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.game.DivineWeather
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the divine weather art to PNGs so it can actually be looked at — the flourishes at a few
 * points through their sweep, and the header medallions ready vs. recharging.
 *
 * Not a golden-image assertion; nothing fails on a pixel diff. Open app/src/test/screenshots/.
 * Run: gradle testDebugUnitTest --tests "*WeatherArtScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class WeatherArtScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val linen = Color(0xFFF1E6CC)

    /** Each weather's field-wide flourish, sampled early / mid / late through its sweep. */
    @Test
    fun weatherFlourishes() {
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                DivineWeather.values().forEach { weather ->
                    Row {
                        listOf(0.15f, 0.5f, 0.85f).forEach { progress ->
                            Canvas(modifier = Modifier.width(140.dp).height(100.dp)) {
                                drawWeatherFlourish(this, weather, progress)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/weather_flourishes.png")
    }

    /** The header medallions: ready, and part-way through re-stitching. */
    @Test
    fun weatherMedallions() {
        composeTestRule.setContent {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFFE5D3B3)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(0f, 0.25f, 0.6f, 0.95f).forEach { cd ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DivineWeather.values().forEach { weather ->
                            WeatherCharge(weather = weather, cooldownFraction = cd, onTrigger = {})
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/weather_medallions.png")
    }
}
