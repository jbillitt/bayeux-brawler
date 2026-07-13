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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the game's own draw functions to PNGs so the art can be looked at without a device.
 *
 * These are NOT golden-image assertions — nothing fails on a pixel diff. They exist so a human can
 * open `app/src/test/screenshots/` and see what the renderer actually draws. The vector builder
 * cannot do this job: its parser only understands literal moveTo/lineTo coordinates, so it renders
 * nothing at all for procedural (looped, seeded) art.
 *
 * Run just these: gradle testDebugUnitTest --tests "*ArtScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class ArtScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val linen = Color(0xFFF1E6CC)

    @Before
    fun loadArt() {
        // The JSON-backed buildings draw nothing without this
        VectorAsset.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
    }

    private fun bgObject(type: BackgroundObjectType, seed: Int = 1) = BackgroundObject(
        id = "shot_${type.name}_$seed",
        type = type,
        posX = 0f,
        width = 300f,
        hp = 500f,
        maxHp = 500f,
        seed = seed
    )

    /** The procedural pair, several seeds each — "no two identical" is the actual requirement. */
    @Test
    fun proceduralBuildings() {
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                listOf(BackgroundObjectType.BUILDING_BAYEUX, BackgroundObjectType.TOWER_SPIRAL).forEach { type ->
                    listOf(listOf(11, 29), listOf(404, 1066)).forEach { seedRow ->
                        Row {
                            seedRow.forEach { seed ->
                                Canvas(modifier = Modifier.width(206.dp).height(200.dp)) {
                                    drawBackgroundObject(this, bgObject(type, seed), size.width / 2f, 0.45f)
                                }
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/procedural.png")
    }

    @Test
    fun buildings() {
        val types = BackgroundObjectType.values().toList()
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                types.chunked(2).forEach { row ->
                    Row {
                        row.forEach { type ->
                            Canvas(modifier = Modifier.width(206.dp).height(150.dp)) {
                                drawBackgroundObject(this, bgObject(type), size.width / 2f, 0.5f)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/buildings.png")
    }

    /** The mounts, at a small size and a normal one — a little man in a chariot draws a second box. */
    @Test
    fun mounts() {
        fun rider(id: String, size: Float, chariot: Boolean = false, stilts: Boolean = false, crown: Boolean = false) =
            FighterState(
                id = id, name = id, isPlayer = true, maxHp = 100f, hp = 100f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_sword" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_chainmail" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == if (crown) "helm_crown" else "helm_none" },
                posX = 0f, targetX = 0f, facingRight = true,
                size = size,
                hairColor = Color(0xFF5A442E), hairStyle = "short",
                isMounted = true,
                isChariot = chariot,
                isStilts = stilts
            )

        val cast = listOf(
            rider("small_chariot", 0.65f, chariot = true),
            rider("big_chariot", 1.3f, chariot = true),
            rider("small_stilts", 0.65f, stilts = true),
            rider("normal_stilts", 1.0f, stilts = true),
            rider("small_horse", 0.65f),
            rider("crowned", 1.0f, crown = true)
        )

        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                cast.chunked(2).forEach { row ->
                    Row {
                        row.forEach { f ->
                            Canvas(modifier = Modifier.width(206.dp).height(190.dp)) {
                                TapestryRenderer.drawCharacter(this, f, scale = 0.8f, isBattleActive = true)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/mounts.png")
    }

    @Test
    fun fighters() {
        fun f(
            id: String,
            armor: String = "armor_bare",
            head: String = "head_bare",
            handle: String = "handle_fists",
            warPaint: Int = 0,
            contagious: Boolean = false,
            priest: Boolean = false
        ) = FighterState(
            id = id, name = id, isPlayer = false,
            maxHp = 100f, hp = 100f,
            weaponHead = GameData.WEAPON_HEADS.first { it.id == head },
            weaponHandle = GameData.WEAPON_HANDLES.first { it.id == handle },
            shield = GameData.SHIELDS.first { it.id == "shield_none" },
            armor = GameData.ARMOR_PIECES.first { it.id == armor },
            headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
            posX = 0f, targetX = 0f, facingRight = true,
            size = 1.0f,
            hairColor = Color(0xFF5A442E),
            hairStyle = "short",
            warPaint = warPaint,
            isContagious = contagious,
            isWarPriest = priest
        )

        val cast = listOf(
            f("plague_peasant", contagious = true),
            f("war_priest_0", priest = true),
            f("saxon_woad_clothed", armor = "armor_padded", head = "head_sword", handle = "handle_medium", warPaint = 1),
            f("saxon_woad_bare", warPaint = 1),
            f("brawler_bare", armor = "armor_bare"),
            f("saxon_mail", armor = "armor_chainmail", head = "head_axe", handle = "handle_medium")
        )

        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                cast.chunked(2).forEach { row ->
                    Row {
                        row.forEach { fighter ->
                            Canvas(modifier = Modifier.width(206.dp).height(190.dp)) {
                                TapestryRenderer.drawCharacter(this, fighter, scale = 1.0f, isBattleActive = true)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/fighters.png")
    }
}
