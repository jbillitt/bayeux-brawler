package com.example.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.CharacterPreviewCard
import com.example.GearSelectionTabs
import com.example.LevelUpScreen
import com.example.MusicDecisionScreen
import com.example.PauseMenuOverlay
import com.example.TrophiesPanel
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

    /** The loadout screen: the densest selection surface in the game, and the most squeezed. */
    @Test
    fun gearSelectionWeaponTab() {
        val gear = (GameData.WEAPON_HEADS.take(9) + GameData.WEAPON_HANDLES.take(4) +
            GameData.SHIELDS.take(3) + GameData.ARMOR_PIECES.take(3) + GameData.HEADGEAR_PIECES.take(3))
            .map { it.id }.toSet()
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF6B5B3E))) {
                GearSelectionTabs(
                    uiState = BattleSimState(unlockedGearIds = gear, level = 4),
                    onSelect = {},
                    onUpdatePhysical = { _, _, _ -> },
                    onToggleDualWield = {},
                    onToggleThroneMode = {}
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/landscape_gear_weapon.png")
    }

    /** The other four tabs share the grid, but Physical is a different layout entirely. */
    @Test
    fun gearSelectionPhysicalTab() {
        val gear = (GameData.WEAPON_HEADS.take(6) + GameData.ARMOR_PIECES.take(4)).map { it.id }.toSet()
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF6B5B3E))) {
                GearSelectionTabs(
                    uiState = BattleSimState(unlockedGearIds = gear, level = 4),
                    onSelect = {}, onUpdatePhysical = { _, _, _ -> },
                    onToggleDualWield = {}, onToggleThroneMode = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Physical 🧍").performClick()
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/landscape_gear_physical.png")
    }

    /** The character card and its mount picklist, which now carries the On Foot row. */
    @Test
    fun characterPreviewWithMounts() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF6B5B3E))) {
                CharacterPreviewCard(
                    uiState = BattleSimState(
                        level = 12,
                        unlockedAncillaries = listOf(
                            Ancillary.WARHORSE, Ancillary.PACK_MULE, Ancillary.WARDOG, Ancillary.HAG
                        ),
                        hasTakenThrone = true
                    )
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/landscape_character_preview.png")
    }

    /** The menu now holds the sound rites, the ad switch and the trophy case. */
    @Test
    fun burgerMenuOutOfBattle() {
        composeTestRule.setContent {
            PauseMenuOverlay(
                musicOn = true, onToggleMusic = {}, canRetire = false,
                inBattle = false, onResume = {}, onRetire = {}
            )
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/landscape_menu_out_of_battle.png")
    }

    /**
     * Mid-battle the same overlay gains the Retire rite, and it must stay on screen. Adding the
     * trophies button and the ad switch to this menu pushed Retire off the bottom edge on a
     * landscape phone — it was still reachable by scrolling, but an irreversible action half
     * off-screen is not "reachable" in any sense that matters. The invariant was a comment in
     * the source; now it is checked, because a comment does not fail a build.
     */
    @Test
    fun pauseMenuInBattleKeepsTheRetireRiteOnScreen() {
        composeTestRule.setContent {
            PauseMenuOverlay(
                musicOn = true, onToggleMusic = {}, canRetire = true,
                inBattle = true, onResume = {}, onRetire = {}
            )
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/landscape_menu_in_battle.png")
        composeTestRule.onNodeWithTag("retire_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("resume_battle_btn").assertIsDisplayed()
    }

    @Test
    fun trophiesPanel() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF1E6CC))) {
                TrophiesPanel(
                    BattleSimState(
                        clearedMilestones = setOf("reach_level_5", "first_siege", "beat_harold")
                    )
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/landscape_trophies.png")
    }

    @Test
    fun musicDecisionScreen() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF1E6CC))) {
                MusicDecisionScreen(options = listOf("Merrier", "More Solemn", "Wilder"), onSelect = {})
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/landscape_music_decision.png")
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
