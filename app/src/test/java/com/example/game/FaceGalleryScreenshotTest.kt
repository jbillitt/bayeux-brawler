package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Heads, big. Brows, noses, jaws, moustaches, haircuts, dog-heads and a parade follower, drawn
 * large enough to actually judge — fighters.png puts whole bodies in a grid at a size where a
 * moustache is four pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h1320dp-xhdpi", sdk = [34])
class FaceGalleryScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val linen = Color(0xFFF2E8D0)

    private fun man(
        nose: Int = 0, bite: Int = 0, brow: Int = 0, tache: Int = 0,
        hair: String = "short", arch: EnemyArchetype? = null, lord: Boolean = false,
        helm: String = "helm_none"
    ) = FighterState(
        id = FighterId("f$nose$bite$brow$tache$hair"), name = "T", isPlayer = false,
        maxHp = 100f, hp = 100f,
        weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
        shield = GameData.SHIELDS.first { it.id == "shield_none" },
        armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
        headgear = GameData.HEADGEAR_PIECES.first { it.id == helm },
        posX = 165f, targetX = 165f, facingRight = true, size = 1f,
        hairColor = Color(0xFF5A442E), hairStyle = hair,
        faceNoseShape = nose, faceBiteShape = bite, faceForehead = brow, faceMustache = tache,
        archetype = arch, isLord = lord
    )

    @Test
    fun headGallery() {
        val rows = listOf(
            // Brows 0-5
            (0..5).map { man(brow = it) },
            // Noses 0-5
            (0..5).map { man(nose = it) },
            // Jaws 0-5
            (0..5).map { man(bite = it) },
            // Moustaches 0-3, then two with helms and a crowned lord
            listOf(man(tache = 0), man(tache = 1), man(tache = 2), man(tache = 3),
                   man(helm = "helm_conical"), man(lord = true, helm = "helm_crown")),
            // Haircuts
            listOf("short", "long", "bald", "hair_braids", "hair_topknot", "hair_germanic")
                .map { man(hair = it) },
            // Dog-heads: every muzzle and ear combination
            (0..3).map { man(nose = it, brow = it % 3, arch = EnemyArchetype.CYNOCEPHALUS) } +
                listOf(man(nose = 1, bite = 1, brow = 2, arch = EnemyArchetype.CYNOCEPHALUS),
                       man(nose = 3, bite = 3, brow = 1, arch = EnemyArchetype.CYNOCEPHALUS))
        )
        composeTestRule.setContent {
            Column(Modifier.fillMaxSize().background(linen)) {
                rows.forEach { r ->
                    Row {
                        r.forEach { f ->
                            Canvas(Modifier.width(165.dp).height(215.dp)) {
                                // Zoom hard on the head: drawCharacter positions from the fighter's
                                // own coordinates, so the crop is done with a transform.
                                withTransform({
                                    scale(1.9f, 1.9f, pivot = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f))
                                    translate(0f, -70f)
                                }) {
                                    TapestryRenderer.drawCharacter(this, f, scale = 1.0f, isBattleActive = true)
                                }
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/face_gallery.png")
    }
}
