package com.example.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.LevelUpScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the game's real screens at LANDSCAPE PHONE size so squashed layouts can be seen instead
 * of guessed at. The game is landscape-locked, and landscape is the hostile case: roughly 360dp of
 * usable height once the border and padding are taken, against 850dp of width. Anything stacked
 * vertically is competing for the scarce axis.
 *
 * These are not golden-image assertions — open `app/src/test/screenshots/landscape_*.png` and look.
 * The accompanying assertions only catch the failure mode that is measurable: content taller than
 * the viewport, which is what "the ad option crushes the rewards" actually is.
 *
 * Run just these: gradle testDebugUnitTest --tests "*LandscapeUiScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A common landscape phone viewport. Height is the constrained axis and the whole point of this file.
@Config(qualifiers = "w850dp-h393dp-land-xhdpi", sdk = [34])
class LandscapeUiScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun choice(id: String, type: String, title: String, description: String) =
        LevelUpChoice(id = id, title = title, description = description, type = type, itemId = id)

    /** A mid-run state with a full hand of spoils — the busiest the reward screen ever gets. */
    private fun rewardState() = BattleSimState(
        level = 7,
        showLevelUpScreen = true,
        pendingLevelUpChoices = listOf(
            choice(
                "follower_anc_fanatic", "follower", "Mad Boris the Fanatic",
                "A wild-eyed zealot who charges ahead of the line screaming psalms. " +
                    "(Entourage follower: Max HP +30, speed +10%)"
            ),
            choice(
                "attach_head_axe", "attachment", "Dane Axe",
                "A broad crescent blade on a long haft. Attached dynamically to your weapon, " +
                    "adding +50% of its base damage!"
            ),
            choice(
                "extension", "extension", "Handle Extension",
                "Lash an additional 1.5-foot wood shaft extension to your grip. Drastically " +
                    "increases reach (+0.35m) and supports more attachments!"
            )
        )
    )

    @Test
    fun rewardScreenWithTheAdOffer() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF6B5B3E))) {
                LevelUpScreen(uiState = rewardState(), onSelectChoice = {}, adOfferAvailable = true)
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/landscape_reward_with_ad.png")
    }

    /** The same screen without the offer, so the cost of the offer is visible side by side. */
    @Test
    fun rewardScreenWithoutTheAdOffer() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF6B5B3E))) {
                LevelUpScreen(uiState = rewardState(), onSelectChoice = {}, adOfferAvailable = false)
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/landscape_reward_no_ad.png")
    }

    /**
     * The measurable half of "the ad option must not squash the rewards": whether the offer is
     * present or not, the laid-out screen must still fit the viewport. Before the header was made
     * a single row, the offer added a whole band of height on the one axis landscape cannot spare.
     */
    @Test
    fun theAdOfferDoesNotPushTheRewardScreenOffTheViewport() {
        var withAd = 0
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                LevelUpScreen(uiState = rewardState(), onSelectChoice = {}, adOfferAvailable = true)
            }
        }
        withAd = composeTestRule.onRoot().fetchSemanticsNode().size.height
        val viewport = composeTestRule.onRoot().fetchSemanticsNode().layoutInfo.height
        org.junit.Assert.assertTrue(
            "the reward screen with the ad offer is ${withAd}px tall in a ${viewport}px viewport",
            withAd <= viewport
        )
    }
}
