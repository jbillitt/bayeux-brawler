package com.example.game

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.example.BayeuxAppContent
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1024dp-h768dp-land", sdk = [36])
class GameIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: GameViewModel

    @Before
    fun setup() {
        viewModel = GameViewModel()
    }

    @Test
    fun testCommenceYeFightStartsBattleWithoutCrashing() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BayeuxAppContent(viewModel = viewModel)
            }
        }

        composeTestRule.waitForIdle()

        // If portrait guide shows up, click bypass.
        val bypassButton = composeTestRule.onNodeWithTag("bypass_rotation_btn")
        try {
            bypassButton.assertIsDisplayed()
            bypassButton.performClick()
            composeTestRule.waitForIdle()
        } catch (e: Throwable) {
            // Already in landscape, no bypass needed
        }

        // Now we should see "COMMENCE YE FIGHT!" button (test tag is "commence_fight_btn")
        val commenceButton = composeTestRule.onNodeWithTag("commence_fight_btn")
        commenceButton.assertIsDisplayed()
        commenceButton.performClick()

        composeTestRule.waitForIdle()

        // Verify state is battle active
        assertTrue("Battle should be active after commencing fight", viewModel.uiState.value.isBattleActive)
        assertTrue("Enemies should be spawned", viewModel.enemiesState.value.isNotEmpty())
    }
}
