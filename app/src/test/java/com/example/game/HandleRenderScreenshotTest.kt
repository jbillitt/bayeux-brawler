package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The seven earned handles, held by a man, so a haft that does not read as its object can be seen.
 * The trumpet was a gold stitched strap — the same primitive that draws arms — and the anchor had
 * no ring, no arms and no flukes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w850dp-h393dp-land-xhdpi", sdk = [34])
class HandleRenderScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun loadArt() {
        VectorAsset.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
    }

    private fun manHolding(handleId: String, headId: String = "head_sword") = FighterState(
        id = FighterId("handle_shot_$handleId"), name = "T", isPlayer = true, maxHp = 100f, hp = 100f,
        weaponHead = GameData.WEAPON_HEADS.first { it.id == headId },
        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == handleId },
        shield = GameData.SHIELDS.first { it.id == "shield_none" },
        armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
        headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_spangen" },
        posX = 0f, targetX = 0f, facingRight = true,
        hairColor = Color(0xFF5A442E), hairStyle = "short"
    )

    @Test
    fun theEarnedHandles() {
        val ids = GameData.UNLOCKABLE_HANDLE_IDS.toList()
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF1E6CC))) {
                ids.chunked(4).forEach { row ->
                    Row {
                        row.forEach { id ->
                            Canvas(modifier = Modifier.width(210.dp).height(196.dp)) {
                                translate(left = size.width / 2f, top = -60f) {
                                    TapestryRenderer.drawCharacter(
                                        this, manHolding(id), scale = 0.62f, isBattleActive = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/handles_earned.png")
    }

    /**
     * Thrusting heads on a long haft. The pitchfork was drawn along the SCREEN axis while the
     * shaft runs about 27 degrees above horizontal, so its tines never lined up with the pole.
     * The spear beside it is the reference: it has always been tilted to match.
     */
    @Test
    fun thrustingHeadsFollowTheHaft() {
        composeTestRule.setContent {
            Row(modifier = Modifier.fillMaxSize().background(Color(0xFFF1E6CC))) {
                listOf("head_pitchfork", "head_axe", "head_spear").forEach { head ->
                    Canvas(modifier = Modifier.width(283.dp).height(393.dp)) {
                        translate(left = size.width / 2f - 70f, top = 95f) {
                            TapestryRenderer.drawCharacter(
                                this, manHolding("handle_medium", head),
                                scale = 0.85f, isBattleActive = false
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/handles_thrusting_heads.png")
    }

    /** The two that were wrong, large, so the flare, ring, stock and flukes are unambiguous. */
    @Test
    fun trumpetAndAnchorCloseUp() {
        composeTestRule.setContent {
            Row(modifier = Modifier.fillMaxSize().background(Color(0xFFF1E6CC))) {
                listOf("handle_trumpet", "handle_anchor").forEach { id ->
                    Canvas(modifier = Modifier.width(425.dp).height(393.dp)) {
                        translate(left = size.width / 2f, top = -120f) {
                            TapestryRenderer.drawCharacter(
                                this, manHolding(id), scale = 1.15f, isBattleActive = false
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/handles_trumpet_anchor.png")
    }
}
