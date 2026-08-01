package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One ship, big, so the stempost's joint to the hull can actually be seen. The whole-set renders
 * put fifteen backdrops in one image at a size where a flimsy joint is a couple of pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w900dp-h600dp-land-xhdpi", sdk = [34])
class ShipProwZoomTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theDragonProwGrowsOutOfTheHull() {
        composeTestRule.setContent {
            Canvas(Modifier.fillMaxSize()) {
                drawBackgroundObject(
                    this,
                    BackgroundObject(
                        id = "ship", type = BackgroundObjectType.SHIP,
                        posX = 0f, width = 300f, hp = 500f, maxHp = 500f, seed = 3
                    ),
                    size.width / 2f,
                    1.5f
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/ship_prow_zoom.png")
    }
}
